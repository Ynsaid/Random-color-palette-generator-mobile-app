package com.example.myply

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.myply.databinding.ActivityColorPaletteBinding
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ColorPaletteActivity : AppCompatActivity() {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var shakeListener: SensorEventListener? = null
    private var lastShakeTime: Long = 0
    private val shakeThreshold = 12.0f // Adjust sensitivity




    private lateinit var binding: ActivityColorPaletteBinding

    private lateinit var card1: CardView
    private lateinit var card2: CardView
    private lateinit var card3: CardView
    private lateinit var card4: CardView
    private lateinit var card5: CardView

    // Data class for API response
    private data class ColormindResponse(val result: List<List<Int>>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityColorPaletteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize CardViews
        card1 = binding.color1
        card2 = binding.color2
        card3 = binding.color3
        card4 = binding.color4
        card5 = binding.color5

        fetchColorPalette()

        binding.generateBtn.setOnClickListener {
            fetchColorPalette()
        }

        // Set up copy listeners (will get updated hex after fetchColorPalette)
        setCopyListeners()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)


        shakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH

                if (acceleration > shakeThreshold) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastShakeTime > 1000) { // Avoid multiple triggers
                        lastShakeTime = currentTime
                        fetchColorPalette()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    private fun setCopyListeners() {
        val copyMap = listOf(
            binding.copyTop to binding.hexBottom,
            binding.copyTop2 to binding.hexBottom2,
            binding.copyTop3 to binding.hexBottom3,
            binding.copyTop4 to binding.hexBottom4,
            binding.copyTop5 to binding.hexBottom5
        )

        for ((copyView, hexView) in copyMap) {
            copyView.setOnClickListener {
                val colorCode = hexView.text.toString()
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Color", colorCode)
                clipboard.setPrimaryClip(clip)

                showCopyNotification()
            }
        }
    }

    private fun showCopyNotification() {
        val notification = binding.copyNotification
        notification.visibility = View.VISIBLE
        notification.animate()
            .translationY(100f)
            .setDuration(400)
            .withEndAction {
                notification.postDelayed({
                    notification.animate()
                        .translationY(-400f)
                        .setDuration(400)

                        .withEndAction {
                            notification.visibility = View.GONE
                        }
                }, 1500)
            }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchColorPalette() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://colormind.io/api/")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val requestBody = """{"model":"default"}"""
                connection.outputStream.bufferedWriter().use {
                    it.write(requestBody)
                    it.flush()
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val colors = Gson().fromJson(response, ColormindResponse::class.java)

                    withContext(Dispatchers.Main) {
                        updateUI(colors.result.map { rgbToHex(it[0], it[1], it[2]) })
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.hexBottom.text = "API Error: ${connection.responseCode}"
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.hexBottom.text = "Error: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun updateUI(colors: List<String>) {
        if (colors.size >= 5) {
            binding.hexBottom.text = colors[0]
            binding.hexBottom2.text = colors[1]
            binding.hexBottom3.text = colors[2]
            binding.hexBottom4.text = colors[3]
            binding.hexBottom5.text = colors[4]

            card1.setCardBackgroundColor(Color.parseColor(colors[0]))
            card2.setCardBackgroundColor(Color.parseColor(colors[1]))
            card3.setCardBackgroundColor(Color.parseColor(colors[2]))
            card4.setCardBackgroundColor(Color.parseColor(colors[3]))
            card5.setCardBackgroundColor(Color.parseColor(colors[4]))
        }
    }

    private fun rgbToHex(r: Int, g: Int, b: Int): String {
        return String.format("#%02X%02X%02X", r, g, b)
    }


    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensor ->
            sensorManager.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(shakeListener)
    }
}
