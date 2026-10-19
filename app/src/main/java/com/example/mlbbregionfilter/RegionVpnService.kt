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

        // IP из PCAP-захвата MLBB для отдельного теста
        private const val TEST_SERVER_IP = "163.171.137.89"
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
            intent?.getStringArrayListExtra(
                EXTRA_COUNTRIES
            ) ?: arrayListOf()

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

            val ipv4Ranges =
                mutableListOf<Pair<String, Int>>()

            val ipv6Ranges =
                mutableListOf<Pair<String, Int>>()

            // Загружаем IP-диапазоны выбранных стран
            for (country in selectedCountries) {

                if (!running.get()) {
                    return
                }

                val code =
                    countryCodes[country]
                        ?: continue

                // IPv4
                loadRanges(
                    "https://www.ipdeny.com/ipblocks/data/countries/$code.zone",
                    true,
                    ipv4Ranges
                )

                // IPv6
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

            // Создаём VPN
            val builder =
                Builder()
                    .setSession("MLBB IP Blocker")
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

            // Блокировка IPv4 выбранных стран
            for (
                (address, prefix)
                in uniqueIpv4
            ) {

                if (!running.get()) {
                    return
                }

                builder