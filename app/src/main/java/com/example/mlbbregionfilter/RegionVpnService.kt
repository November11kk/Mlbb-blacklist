package com.example.mlbbregionfilter

import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class RegionVpnService : VpnService() {

    companion object {
        const val EXTRA_COUNTRIES = "selected_countries"

        private const val TAG = "MLBBFilter"
        private const val MLBB_PACKAGE = "com.mobile.legends"

        private const val VPN_IPV4 = "10.10.0.2"
        private const val VPN_IPV6 = "fd00:1:1::2"
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

            val ipv4Ranges = mutableListOf<Pair<String, Int>>()
            val ipv6Ranges = mutableListOf<Pair<String, Int>>()

            for (country in selectedCountries) {

                if (!running.get()) return

                val code = countryCodes[country] ?: continue

                loadRanges(
                    "https://www.ipdeny.com/ipblocks/data/countries/$code.zone",
                    true,
                    ipv4Ranges
                )

                loadRanges(
                    "https://www.ipdeny.com/ipv6/ipaddresses/blocks/$code.zone",
                    false,
                    ipv6Ranges
                )
            }

            val uniqueIpv4 = ipv4Ranges.distinct()
            val uniqueIpv6 = ipv6Ranges.distinct()

            if (uniqueIpv4.isEmpty() && uniqueIpv6.isEmpty()) {
                throw IllegalStateException(
                    "Не удалось загрузить IP-диапазоны"
                )
            }

            Log.i(
                TAG,
                "Загружено: IPv4=${uniqueIpv4.size}, IPv6=${uniqueIpv6.size}"
            )

            val builder = Builder()
                .setSession("MLBB IP Blocker")
                .setMtu(1500)

                .addAddress(VPN_IPV4, 32)
                .addAddress(VPN_IPV6, 64)

                // VPN применяется только к Mobile Legends
                .addAllowedApplication(MLBB_PACKAGE)

            /*
             * Украинские IPv4 сети направляются в VPN.
             * Пакеты из TUN мы не пересылаем дальше.
             * Поэтому они блокируются.
             */
            for ((address, prefix) in uniqueIpv4) {

                if (!running.get()) return

                builder.addRoute(address, prefix)
            }

            /*
             * То же самое для IPv6.
             */
            for ((address, prefix) in uniqueIpv6) {

                if (!running.get()) return

                builder.addRoute(address, prefix)
            }

            localInterface = builder.establish()
                ?: throw IllegalStateException(
                    "Android не создал VPN-интерфейс"
                )

            vpnInterface = localInterface

            showToast(
                "Блокировка запущена\n" +
                    "IPv4: ${uniqueIpv4.size}\n" +
                    "IPv6: ${uniqueIpv6.size}"
            )

            Log.i(
                TAG,
                "HARD BLOCK запущен"
            )

            /*
             * Всё, что попало в TUN,
             * НЕ отправляется обратно в интернет.
             */
            FileInputStream(localInterface.fileDescriptor).use { input ->

                val buffer = ByteArray(32767)

                var packetCount = 0
                val loggedDestinations = mutableSetOf<String>()

                while (running.get()) {

                    val length = input.read(buffer)

                    if (length < 0) break
                    if (length == 0) continue

                    packetCount++

                    val version =
                        (buffer[0].toInt() ushr 4) and 0x0F

                    when (version) {

                        4 -> {

                            if (length >= 20) {

                                val destinationBytes =
                                    buffer.copyOfRange(16, 20)

                                val destination =
                                    InetAddress
                                        .getByAddress(destinationBytes)
                                        .hostAddress

                                if (
                                    loggedDestinations.add(destination) &&
                                    loggedDestinations.size <= 100
                                ) {

                                    Log.d(
                                        TAG,
                                        "BLOCK IPv4 -> $destination"
                                    )
                                }
                            }
                        }

                        6 -> {

                            if (length >= 40) {

                                val destinationBytes =
                                    buffer.copyOfRange(24, 40)

                                val destination =
                                    InetAddress
                                        .getByAddress(destinationBytes)
                                        .hostAddress

                                if (
                                    loggedDestinations.add(destination) &&
                                    loggedDestinations.size <= 100
                                ) {

                                    Log.d(
                                        TAG,
                                        "BLOCK IPv6 -> $destination"
                                    )
                                }
                            }
                        }
                    }

                    if (packetCount % 1000 == 0) {

                        Log.i(
                            TAG,
                            "Заблокировано пакетов: $packetCount"
                        )
                    }
                }
            }

        } catch (e: Exception) {

            if (running.get()) {

                Log.e(
                    TAG,
                    "Ошибка VPN-фильтра",
                    e
                )

                showToast(
                    "Ошибка фильтра: ${e.message ?: "неизвестная"}"
                )
            }

        } finally {

            running.set(false)

            try {
                localInterface?.close()
            } catch (_: Exception) {
            }

            if (vpnInterface === localInterface) {
                vpnInterface = null
            }

            stopSelf()
        }
    }

    private fun loadRanges(
        urlString: String,
        ipv4: Boolean,
        target: MutableList<Pair<String, Int>>
    ) {

        val connection =
            URL(urlString).openConnection() as HttpURLConnection

        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.requestMethod = "GET"

        try {

            connection.inputStream.bufferedReader().useLines { lines ->

                lines.forEach { rawLine ->

                    if (!running.get()) return@useLines

                    val line = rawLine.trim()

                    if (
                        line.isEmpty() ||
                        line.startsWith("#")
                    ) {
                        return@forEach
                    }

                    val parts = line.split("/")

                    if (parts.size != 2) {
                        return@forEach
                    }

                    val address = parts[0].trim()

                    val prefix =
                        parts[1].trim().toIntOrNull()
                            ?: return@forEach

                    if (ipv4) {

                        if (
                            prefix in 0..32 &&
                            address.contains(".") &&
                            !address.contains(":")
                        ) {

                            target.add(
                                address to prefix
                            )
                        }

                    } else {

                        if (
                            prefix in 0..128 &&
                            address.contains(":")
                        ) {

                            target.add(
                                address to prefix
                            )
                        }
                    }
                }
            }

        } finally {

            connection.disconnect()
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

        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }

        vpnInterface = null
    }

    override fun onDestroy() {

        stopVpn()

        super