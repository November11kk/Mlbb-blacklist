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

        const val ACTION_STOP = "stop_vpn"

        private const val TAG = "MLBBFilter"

        private const val MLBB_PACKAGE = "com.mobile.legends"

        private const val VPN_IPV4 = "10.10.0.2"

        private const val VPN_IPV6 = "fd00:1:1::2"

        /*
         * ВСЕ IP, которые ранее фигурировали
         * в анализах PCAP/MLBB.
         *
         * Каждый одиночный IP блокируется как /32.
         */

        private val PCAP_IPS = listOf(

            // Основной игровой сервер из PCAP
            "163.171.137.89",

            // Другие соединения из PCAP
            "104.166.152.2",
            "161.202.222.9",
            "154.85.91.19",
            "23.64.12.171",

            // Ранее обнаруженные MLBB IP
            "107.155.47.0"
        )

        /*
         * Сети, которые ранее фигурировали
         * при исследовании MLBB.
         *
         * Они добавляются как маршруты целиком.
         */

        private val MLBB_NETWORKS = listOf(

            "103.157.33.0/24",

            "216.133.157.0/24",

            "23.248.170.0/24",

            "23.248.163.0/24",

            "202.52.240.0/21",

            "156.59.0.0/16",

            "47.236.0.0/16",

            "161.117.0.0/16",

            "103.157.32.0/24",

            "101.255.0.0/16",

            "103.111.144.0/23",

            "45.121.218.0/23",

            "129.227.0.0/16",

            "161.202.0.0/16"
        )
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

        /*
         * Отдельная команда остановки VPN.
         */

        if (intent?.action == ACTION_STOP) {

            stopVpn()

            stopSelf(startId)

            return START_NOT_STICKY
        }

        /*
         * Если VPN уже работал —
         * сначала полностью останавливаем старый экземпляр.
         */

        stopVpn()

        val selectedCountries =
            intent?.getStringArrayListExtra(EXTRA_COUNTRIES)
                ?: arrayListOf()

        if (selectedCountries.isEmpty()) {

            showToast(
                "Сначала выбери страну для блокировки"
            )

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

    private fun runVpn(
        selectedCountries: List<String>
    ) {

        var localInterface: ParcelFileDescriptor? = null

        try {

            val ipv4Ranges =
                mutableListOf<Pair<String, Int>>()

            val ipv6Ranges =
                mutableListOf<Pair<String, Int>>()

            /*
             * Загружаем IP-диапазоны выбранных стран.
             */

            for (country in selectedCountries) {

                if (!running.get()) {
                    return
                }

                val code =
                    countryCodes[country]
                        ?: continue

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

            val uniqueIpv4 =
                ipv4Ranges.distinct()

            val uniqueIpv6 =
                ipv6Ranges.distinct()

            if (
                uniqueIpv4.isEmpty() &&
                uniqueIpv6.isEmpty()
            ) {

                throw IllegalStateException(
                    "Не удалось загрузить IP-диапазоны"
                )
            }

            Log.i(
                TAG,
                "Загружено: IPv4=${uniqueIpv4.size}, IPv6=${uniqueIpv6.size}"
            )

            /*
             * Создаём VPN.
             */

            val builder =
                Builder()
                    .setSession(
                        "MLBB IP Blocker"
                    )
                    .setMtu(1500)
                    .addAddress(
                        VPN_IPV4,
                        32
                    )
                    .addAddress(
                        VPN_IPV6,
                        64
                    )
                    .addAllowedApplication(
                        MLBB_PACKAGE
                    )

            /*
             * =========================================================
             * 1. БЛОКИРОВКА IP ВЫБРАННЫХ СТРАН
             * =========================================================
             */

            for (
                (address, prefix)
                in uniqueIpv4
            ) {

                if (!running.get()) {
                    return
                }

                builder.addRoute(
                    address,
                    prefix
                )
            }

            /*
             * IPv6 выбранных стран.
             */

            for (
                (address, prefix)
                in uniqueIpv6
            ) {

                if (!running.get()) {
                    return
                }

                builder.addRoute(
                    address,
                    prefix
                )
            }

            /*
             * =========================================================
             * 2. БЛОКИРОВКА ВСЕХ ОТДЕЛЬНЫХ IP ИЗ PCAP
             * =========================================================
             */

            for (ip in PCAP_IPS) {

                if (!running.get()) {
                    return
                }

                try {

                    builder.addRoute(
                        ip,
                        32
                    )

                    Log.i(
                        TAG,
                        "PCAP BLOCK -> $ip/32"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Не удалось добавить PCAP IP: $ip",
                        e
                    )
                }
            }

            /*
             * =========================================================
             * 3. БЛОКИРОВКА РАНЕЕ НАЙДЕННЫХ MLBB СЕТЕЙ
             * =========================================================
             */

            for (network in MLBB_NETWORKS) {

                if (!running.get()) {
                    return
                }

                val parts =
                    network.split("/")

                if (parts.size != 2) {
                    continue
                }

                val address =
                    parts[0]

                val prefix =
                    parts[1].toIntOrNull()
                        ?: continue

                try {

                    builder.addRoute(
                        address,
                        prefix
                    )

                    Log.i(
                        TAG,
                        "MLBB NETWORK BLOCK -> $network"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Не удалось добавить сеть: $network",
                        e
                    )
                }
            }

            /*
             * Создаём VPN-интерфейс.
             */

            localInterface =
                builder.establish()
                    ?: throw IllegalStateException(
                        "Android не создал VPN-интерфейс"
                    )

            vpnInterface = localInterface

            showToast(
                "Блокировка запущена\n" +
                    "Стран: ${selectedCountries.size}\n" +
                    "PCAP IP: ${PCAP_IPS.size}\n" +
                    "MLBB сетей: ${MLBB_NETWORKS.size}"
            )

            Log.i(
                TAG,
                "HARD BLOCK запущен"
            )

            /*
             * =========================================================
             * ЧИТАЕМ ПАКЕТЫ ИЗ TUN
             * =========================================================
             *
             * Пакеты сюда попадают потому, что для соответствующих
             * адресов выше были созданы VPN routes.
             *
             * Мы их НЕ пересылаем дальше.
             *
             * То есть это настоящий blackhole для этих маршрутов.
             */

            FileInputStream(
                localInterface.fileDescriptor
            ).use { input ->

                val buffer =
                    ByteArray(32767)

                var packetCount = 0

                val loggedDestinations =
                    mutableSetOf<String>()

                while (running.get()) {

                    val length =
                        input.read(buffer)

                    if (length < 0) {
                        break
                    }

                    if (length == 0) {
                        continue
                    }

                    packetCount++

                    /*
                     * Определяем IP-версию.
                     */

                    val version =
                        (
                            buffer[0].toInt()
                                ushr 4
                        ) and 0x0F

                    when (version) {

                        /*
                         * IPv4
                         */

                        4 -> {

                            if (length >= 20) {

                                val destinationBytes =
                                    buffer.copyOfRange(
                                        16,
                                        20
                                    )

                                val destination =
                                    InetAddress
                                        .getByAddress(
                                            destinationBytes
                                        )
                                        .hostAddress

                                if (
                                    loggedDestinations.add(
                                        destination
                                    ) &&
                                    loggedDestinations.size <= 200
                                ) {

                                    Log.d(
                                        TAG,
                                        "BLOCK IPv4 -> $destination"
                                    )
                                }
                            }
                        }

                        /*
                         * IPv6
                         */

                        6 -> {

                            if (length >= 40) {

                                val destinationBytes =
                                    buffer.copyOfRange(
                                        24,
                                        40
                                    )

                                val destination =
                                    InetAddress
                                        .getByAddress(
                                            destinationBytes
                                        )
                                        .hostAddress

                                if (
                                    loggedDestinations.add(
                                        destination
                                    ) &&
                                    loggedDestinations.size <= 200
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
                    "Ошибка фильтра: ${
                        e.message ?: "неизвестная"
                    }"
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
            URL(urlString)
                .openConnection() as HttpURLConnection

        connection.connectTimeout =
            15000

        connection.readTimeout =
            20000

        connection.requestMethod =
            "GET"

        try {

            connection.inputStream
                .bufferedReader()
                .useLines { lines ->

                    lines.forEach { rawLine ->

                        if (!running.get()) {
                            return@useLines
                        }

                        val line =
                            rawLine.trim()

                        if (
                            line.isEmpty() ||
                            line.startsWith("#")
                        ) {
                            return@forEach
                        }

                        val parts =
                            line.split("/")

                        if (parts.size != 2) {
                            return@forEach
                        }

                        val address =
                            parts[0].trim()

                        val prefix =
                            parts[1]
                                .trim()
                                .toIntOrNull()
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

    private fun showToast(
        message: String
    ) {

        Handler(
            Looper.getMainLooper()
        ).post {

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

        super.onDestroy()
    }
}