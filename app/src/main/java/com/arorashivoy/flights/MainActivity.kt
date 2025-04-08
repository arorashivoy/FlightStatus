package com.arorashivoy.flights

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.buttonScreen1).setOnClickListener {
            startActivity(Intent(this, Screen1Activity::class.java))
        }

        findViewById<Button>(R.id.buttonScreen2).setOnClickListener {
            startActivity(Intent(this, Screen2Activity::class.java))
        }
    }
}