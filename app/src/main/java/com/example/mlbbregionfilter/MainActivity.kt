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

        // Заголовок
        val title = TextView(this).apply {
            text = "MLBB Region Filter"
            textSize = 25f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        }

        layout.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Описание
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

        layout.addView(
            description,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Заголовок выбора стран
        val countryTitle = TextView(this).apply {
            text = "Страны для блокировки"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        }

        layout.addView(
            countryTitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Украина
        ukraineCheckBox = CheckBox(this).apply {
            text = "Украина — IPv4 + IPv6"
            textSize = 16f
            setTextColor(Color.WHITE)
            isChecked = true
            setPadding(0, dp(4), 0, dp(14))
        }

        layout.addView(
            ukraineCheckBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Кнопка запуска
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

        // Кнопка остановки
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
                ViewGroup.Layout