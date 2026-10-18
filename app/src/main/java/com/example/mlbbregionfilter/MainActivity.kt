
package com.example.mlbbregionfilter

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val countries = listOf(
        "Украина",
        "Италия",
        "Германия",
        "Польша",
        "Франция",
        "Нидерланды",
        "Румыния",
        "Турция"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)

        val title = TextView(this)
        title.text = "MLBB Region Filter"
        title.textSize = 24f
        layout.addView(title)

        val description = TextView(this)
        description.text =
            "Выбор регионов. Сетевой движок пока не готов."
        layout.addView(description)

        for (country in countries) {
            val checkBox = CheckBox(this)
            checkBox.text = country
            layout.addView(checkBox)
        }

        val startButton = Button(this)
        startButton.text = "Запустить фильтр"
        startButton.setOnClickListener {
            Toast.makeText(
                this,
                "Фильтр пока отключён: пересылка трафика не реализована.",
                Toast.LENGTH_LONG
            ).show()
        }
        layout.addView(startButton)

        val stopButton = Button(this)
        stopButton.text = "Остановить фильтр"
        stopButton.setOnClickListener {
            stopService(
                Intent(this, RegionVpnService::class.java)
            )
            Toast.makeText(
                this,
                "Команда остановки отправлена.",
                Toast.LENGTH_SHORT
            ).show()
        }
        layout.addView(stopButton)

        setContentView(layout)
    }
}
