package com.arorashivoy.flights

import com.arorashivoy.flights.BuildConfig

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

@Entity(tableName = "flights")
data class Flight(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val flightNumber: String,
    val departure: String,
    val arrival: String,
    val departureTime: Long,
    val arrivalTime: Long
)

@Dao
interface FlightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlights(flights: List<Flight>)

    @Query("SELECT * FROM flights WHERE departure = :dep AND arrival = :arr")
    suspend fun getFlights(dep: String, arr: String): List<Flight>
}

@Database(entities = [Flight::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flightDao(): FlightDao
}

object FlightDatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "flights.db"
            ).build()
            INSTANCE = instance
            instance
        }
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}

object FlightRepository {
    private val client = OkHttpClient()
    private val API_KEY = BuildConfig.AVIATIONSTACK_API_KEY

    suspend fun fetchFlightData(depIATA: String, arrIATA: String) : List<Flight> {
        val url = "https://api.aviationstack.com/v1/flights?access_key=$API_KEY&dep_iata=$depIATA&arr_iata=$arrIATA"
        val request = Request.Builder().url(url).build()
        val flightsData: MutableList<Flight> = mutableListOf()

        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Log.e("FlightFetcher", "Error fetching flight data", e)
                    continuation.resumeWith(Result.success(emptyList()))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (!response.isSuccessful) {
                        Log.e("FlightFetcher", "Unexpected code $response")
                        continuation.resumeWith(Result.success(emptyList()))
                        return
                    }

                    response.body?.let { body ->
                        val responseStr = body.string()
                        // Parse and store the flight data here
                        try {
                            val jsonObject = JsonParser.parseString(responseStr).asJsonObject
                            val data = jsonObject.getAsJsonArray("data")

                            for (i in 0 until data.size()) {
                                val flightObject = data[i].asJsonObject

                                if (flightObject.get("flight_status").asString != "landed") {
                                    continue
                                }
                                val flight = flightObject.getAsJsonObject("flight")
                                val departure = flightObject.getAsJsonObject("departure")
                                val arrival = flightObject.getAsJsonObject("arrival")

                                val depTime = Instant.parse(departure.get("actual").asString).toEpochMilli()
                                val arrTime = Instant.parse(arrival.get("actual").asString).toEpochMilli()

                                flightsData += Flight(
                                    flightNumber = flight.get("iata").asString,
                                    departure = departure.get("iata").asString,
                                    arrival = arrival.get("iata").asString,
                                    departureTime = depTime,
                                    arrivalTime = arrTime
                                )

                            }

                            continuation.resumeWith(Result.success(flightsData))

                        } catch (e: Exception) {
                            Log.e("FlightFetcher", "Error parsing flight data", e)
                            continuation.resumeWith(Result.success(emptyList()))
                        }
                    }
                }
            })
        }
    }
}

class FlightApiWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    private val db = FlightDatabaseProvider.getDatabase(applicationContext)

    private val airportCodes = listOf("DEL", "GOI", "BOM")
    override suspend fun doWork(): Result {
        return try {
            // Fetch flight data for all combinations of airport codes
            for (dep in airportCodes) {
                for (arr in airportCodes) {
                    if (dep != arr) {
                        Log.d("FlightApiWorker", "Fetching data for $dep to $arr")
                        val flights = FlightRepository.fetchFlightData(dep, arr)
                        if (flights.isNotEmpty()) {
                            db.flightDao().insertFlights(flights)
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("FlightApiWorker", "Background fetch failed", e)
            Result.failure()
        }
    }
}

fun scheduleApiFetchWorker(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<FlightApiWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "FlightApiFetch",
        ExistingPeriodicWorkPolicy.UPDATE,
        workRequest
    )
}

class Screen2Activity : AppCompatActivity() {
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FlightDatabaseProvider.getDatabase(this)

        scheduleApiFetchWorker(this)
        setContentView(R.layout.activity_screen2)


        val depEdit = findViewById<EditText>(R.id.depEdit)
        val arrEdit = findViewById<EditText>(R.id.arrEdit)
        val btnAvg = findViewById<Button>(R.id.btnAvg)
        val resultView = findViewById<TextView>(R.id.resultView)

        btnAvg.setOnClickListener {
            val dep = depEdit.text.toString().uppercase(Locale.ROOT)
            val arr = arrEdit.text.toString().uppercase(Locale.ROOT)

            lifecycleScope.launch {
                var flights = db.flightDao().getFlights(dep, arr)
                if (flights.isEmpty()) {
                    Log.d("FlightFetcher", "Fetching new data for $dep to $arr")
                    flights = FlightRepository.fetchFlightData(dep, arr)
                    if (flights.isNotEmpty()) {
                        db.flightDao().insertFlights(flights)
                    } else {
                        runOnUiThread {
                            resultView.text = "No flights found."
                        }
                        return@launch
                    }
                }
                val avgTimeMillis = flights.map { it.arrivalTime - it.departureTime }.average()
                val minutes = TimeUnit.MILLISECONDS.toMinutes(avgTimeMillis.toLong())
                runOnUiThread {
                    resultView.text = "Average Flight Time: $minutes minutes"
                }
            }
        }
    }
}