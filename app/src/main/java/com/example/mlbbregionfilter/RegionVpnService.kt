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
                if (!running