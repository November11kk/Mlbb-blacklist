package com.example.mlbbregionfilter

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var ukraineCheckBox: CheckBox
    private val vpnRequestCode = 1001

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(
                dp(20),
                dp(20),
                dp(20),
                dp(24)
            )
        }

        val title = TextView(this).apply {
            text = "MLBB Region Filter"
            textSize = 25f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        }

        layout.addView(title)

        val description = TextView(this).apply {
            text = """
                Фильтр IP-трафика Mobile Legends.

                Выбери страну, IP-диапазоны которой
                необходимо заблокировать.

                VPN применяется только к Mobile Legends.
                Сторонний VPN-сервер не используется.

                Важно: блокировка IP-диапазонов страны
                не гарантирует смену игрового региона MLBB.
            """.trimIndent()

            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(18))
        }

        layout.addView(description)

        val countryTitle = TextView(this).apply {
            text = "Страны для блокировки"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        }

        layout.addView(countryTitle)

        ukraineCheckBox = CheckBox(this).apply {
            text = "Украина — IPv4 + IPv6"
            textSize = 16f
            setTextColor(Color.WHITE)
            isChecked = true
            setPadding(
                0,
                dp(4),
                0,
                dp(14)
            )
        }

        layout.addView(ukraineCheckBox)

        val startButton = Button(this).apply {
            text = "Заблокировать выбранные страны"
            textSize = 16f
            minHeight = dp(52)

            setOnClickListener {
                val selected = arrayListOf<String>()

                if (ukraineCheckBox.isChecked) {
                    selected.add("Украина")
                }

                if (selected.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Выбери хотя бы одну страну",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setOnClickListener
                }

                val prepareIntent =
                    VpnService.prepare(this@MainActivity)

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

        layout.addView(
            startButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        )

        val stopButton = Button(this).apply {
            text = "Остановить блокировку"
            textSize = 16f
            minHeight = dp(52)

            setOnClickListener {
                stopService(
                    Intent(
                        this@MainActivity,
                        RegionVpnService::class.java
                    )
                )

                Toast.makeText(
                    this@MainActivity,
                    "Блокировка остановлена",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        layout.addView(
            stopButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        scrollView.addView(
            layout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(scrollView)
    }

    @Deprecated("Используется для совместимости")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode != vpnRequestCode) {
            return
        }

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
                    "Выбери хотя бы одну страну",
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

    private fun startFilter(
        selected: ArrayList<String>
    ) {
        val intent = Intent(
            this,
            RegionVpnService::class.java
        )

        intent.putStringArrayListExtra(
            RegionVpnService.EXTRA_COUNTRIES,
            selected
        )

        startService(intent)
    }
}