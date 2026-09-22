package com.arorashivoy.flights

import com.arorashivoy.flights.BuildConfig

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import okhttp3.OkHttpClient
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException


class Screen1Activity : AppCompatActivity() {
    private lateinit var flightInput: EditText
    private lateinit var trackButton: Button
    private lateinit var flightInfo: TextView
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var trackingRunnable: Runnable? = null
    private val API_KEY = BuildConfig.AVIATIONSTACK_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen1)

        flightInput = findViewById(R.id.flightInput)
        trackButton = findViewById(R.id.trackButton)
        flightInfo = findViewById(R.id.flightInfo)

        trackButton.setOnClickListener {
            val flightNumber = flightInput.text.toString().trim()
            if (flightNumber.isEmpty()) {
                flightInfo.text = "Please enter a flight number"
            } else {
                startTracking(flightNumber)
            }
        }
    }

    private fun startTracking(flightNumber: String) {
        trackingRunnable?.let { handler.removeCallbacks(it) }

        trackingRunnable = object : Runnable {
            override fun run() {
                fetchFlightData(flightNumber)
                handler.postDelayed(this, 60000)
            }
        }

        handler.post(trackingRunnable!!)
    }

    private fun fetchFlightData(flightNumber: String) {
        val url = "https://api.aviationstack.com/v1/flights?access_key=$API_KEY&flight_iata=$flightNumber"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { flightInfo.text = "API request failed: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { body ->
                    val responseStr = body.string()
//                    Log.INFO("SHIVOY", responseStr)
//                    Log.d("SHIVOY_TEST", responseStr)

                    try {
                        val jsonObject = JsonParser.parseString(responseStr).asJsonObject
                        val data = jsonObject.getAsJsonArray("data")
//                        Log.d("SHIVOY_TEST", data.toString())
                        if (data.size() > 0) {
                            var flightData: JsonObject? = null
                            for (i in 0 until data.size()) {
                                if (data[i].asJsonObject["flight_status"].asString == "active") {
//                                    Log.d("SHIVOY_TEST", data[i].asJsonObject["flight_status"].asString)
                                    flightData = data[i].asJsonObject
                                }
                            }

                            if (flightData == null) {
                                runOnUiThread { flightInfo.text = "Flight is not flying currently" }
                                return
                            }
                            val dep = flightData["departure"].asJsonObject["airport"].asString
                            val arr = flightData["arrival"].asJsonObject["airport"].asString
                            val lat = flightData["live"].asJsonObject["latitude"].asString
                            val lon = flightData["live"].asJsonObject["longitude"].asString
                            val speed = flightData["live"].asJsonObject["speed_horizontal"].asString
                            val info = "From: $dep\nTo: $arr\nLatitude: $lat\nLongitude: $lon\nSpeed: $speed km/h"

                            runOnUiThread { flightInfo.text = info }
                        } else {
                            runOnUiThread { flightInfo.text = "Flight not found." }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { flightInfo.text = "Error parsing data." }
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        trackingRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}