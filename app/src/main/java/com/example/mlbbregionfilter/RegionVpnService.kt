package com.example.mlbbregionfilter

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class RegionVpnService : VpnService() {

    companion object {
        const val EXTRA_COUNTRIES = "selected_countries"

        private const val MLBB_PACKAGE = "com.mobile.legends"
        private const val VPN_ADDRESS = "10.10.0.2"
    }

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null

    private var workerThread: Thread? = null
    private val running = AtomicBoolean(false)

    private val countryCodes = mapOf(
        "Украина" to "ua",
        "Италия" to "it",
        "Германия" to "de",
        "Польша" to "pl",
        "Франция" to "fr",
        "Нидерланды" to "nl",
        "Румыния" to "ro",
        "Турция" to "tr"
    )

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        stopVpn()

        val selectedCountries =
            intent?.getStringArrayListExtra(EXTRA_COUNTRIES)
                ?: arrayListOf()

        if (selectedCountries.isEmpty()) {
            showToast("Сначала выбери страну для блокировки")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        running.set(true)

        workerThread = Thread {
            runVpn(selectedCountries)
        }.also {
            it.name = "MLBB-IP-Filter"
            it.start()
        }

        return START_NOT_STICKY
    }

    private fun runVpn(selectedCountries: List<String>) {
        var localInterface: ParcelFileDescriptor? = null

        try {
            val ranges = mutableListOf<Pair<String, Int>>()

            for (country in selectedCountries) {
                if (!running.get()) return

                val code = countryCodes[country] ?: continue
                val url = URL(
                    "https://www.ipdeny.com/ipblocks/data/countries/$code.zone"
                )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.requestMethod = "GET"

                try {
                    connection.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val parts = line.trim().split("/")
                            if (parts.size == 2) {
                                val prefix = parts[1].toIntOrNull()
                                if (
                                    prefix != null &&
                                    prefix in 0..32 &&
                                    parts[0].contains(".")
                                ) {
                                    ranges.add(parts[0] to prefix)
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }

            if (!running.get()) return

            if (ranges.isEmpty()) {
                throw IllegalStateException(
                    "Не удалось загрузить IP-диапазоны"
                )
            }

            val builder = Builder()
                .setSession("MLBB IP Blocker")
                .setMtu(1500)
                .addAddress(VPN_ADDRESS, 32)
                .addAllowedApplication(MLBB_PACKAGE)

            for ((address, prefix) in ranges.distinct()) {
                if (!running.get()) return
                builder.addRoute(address, prefix)
            }

            localInterface = builder.establish()
                ?: throw IllegalStateException(
                    "Android не создал VPN-интерфейс"
                )

            vpnInterface = localInterface

            showToast(
                "Фильтр запущен. Диапазонов: ${ranges.distinct().size}"
            )

            /*
             * В VPN попадают только выбранные IP-диапазоны.
             * Полученные пакеты отбрасываются.
             * Остальные IPv4-адреса не маршрутизируются в этот VPN.
             */
            FileInputStream(localInterface.fileDescriptor).use { input ->
                val buffer = ByteArray(32767)

                while (running.get()) {
                    val length = input.read(buffer)
                    if (length < 0) break
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                Log.e("MLBBFilter", "Ошибка VPN-фильтра", e)
                showToast("Ошибка фильтра: ${e.message ?: "неизвестная"}")
            }
        } finally {
            running.set(false)
            localInterface?.close()

            if (vpnInterface === localInterface) {
                vpnInterface = null
            }

            stopSelf()
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopVpn() {
        running.set(false)

        workerThread?.interrupt()
        workerThread = null

        vpnInterface?.close()
        vpnInterface = null
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}