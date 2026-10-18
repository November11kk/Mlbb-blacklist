
package com.example.mlbbregionfilter

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var ukraineCheckBox: CheckBox
    private val vpnRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "MLBB Region Filter"
            textSize = 24f
        }
        layout.addView(title)

        val description = TextView(this).apply {
            text = """
                Тестовый режим блокировки регионов.

                Выбирай диапазоны IP, которые нужно блокировать.
                Остальной трафик не должен перенаправляться
                через сторонний сервер.
            """.trimIndent()
        }
        layout.addView(description)

        ukraineCheckBox = CheckBox(this).apply {
            text = "Украина — блокировать все IPv4-диапазоны"
            isChecked = true
        }
        layout.addView(ukraineCheckBox)

        val startButton = Button(this).apply {
            text = "Заблокировать Украину"
            setOnClickListener {
                val selected = arrayListOf<String>()

                if (ukraineCheckBox.isChecked) {
                    selected.add("Украина")
                }

                if (selected.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Сначала выбери регион для блокировки",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                val prepareIntent = VpnService.prepare(this@MainActivity)

                if (prepareIntent != null) {
                    startActivityForResult(
                        prepareIntent,
                        vpnRequestCode
                    )
                } else {
                    startFilter(selected)
                }
            }
        }
        layout.addView(startButton)

        val stopButton = Button(this).apply {
            text = "Остановить блокировку"
            setOnClickListener {
                stopService(
                    Intent(
                        this@MainActivity,
                        RegionVpnService::class.java
                    )
                )
                Toast.makeText(
                    this@MainActivity,
                    "Команда остановки отправлена",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        layout.addView(stopButton)

        setContentView(layout)
    }

    @Deprecated("Используется для совместимости с текущим прототипом")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == vpnRequestCode) {
            if (resultCode == Activity.RESULT_OK) {
                val selected = arrayListOf<String>()

                if (ukraineCheckBox.isChecked) {
                    selected.add("Украина")
                }

                if (selected.isNotEmpty()) {
                    startFilter(selected)
                } else {
                    Toast.makeText(
                        this,
                        "Выбери регион для блокировки",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Разрешение VPN не предоставлено",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startFilter(selected: ArrayList<String>) {
        val intent = Intent(this, RegionVpnService::class.java)
        intent.putStringArrayListExtra(
            RegionVpnService.EXTRA_COUNTRIES,
            selected
        )
        startService(intent)
    }
}
