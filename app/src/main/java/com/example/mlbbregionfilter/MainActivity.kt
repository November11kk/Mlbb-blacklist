package com.example.mlbbregionfilter

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
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
            "Выберите страны, IP-адреса которых разрешены для Mobile Legends."

        layout.addView(description)

        val checkBoxes = mutableListOf<CheckBox>()

        for (country in countries) {
            val checkBox = CheckBox(this)
            checkBox.text = country
            checkBoxes.add(checkBox)
            layout.addView(checkBox)
        }

        val startButton = Button(this)
        startButton.text = "Запустить фильтр"

        startButton.setOnClickListener {

            val selectedCountries = checkBoxes
                .filter { it.isChecked }
                .map { it.text.toString() }

            val intent = Intent(this, RegionVpnService::class.java)

            intent.putStringArrayListExtra(
                RegionVpnService.EXTRA_COUNTRIES,
                ArrayList(selectedCountries)
            )

            val vpnIntent = VpnService.prepare(this)

            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, 100)
            } else {
                startService(intent)
            }
        }

        layout.addView(startButton)

        val stopButton = Button(this)
        stopButton.text = "Остановить фильтр"

        stopButton.setOnClickListener {
            stopService(
                Intent(this, RegionVpnService::class.java)
            )
        }

        layout.addView(stopButton)

        setContentView(layout)
    }
}
