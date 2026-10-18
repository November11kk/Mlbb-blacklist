
package com.example.mlbbregionfilter

import android.content.Intent
import android.net.VpnService

class RegionVpnService : VpnService() {

    companion object {
        const val EXTRA_COUNTRIES = "selected_countries"
        const val ACTION_STOP = "com.example.mlbbregionfilter.STOP"
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        // Без готового механизма пересылки пакетов
        // VPN запускать нельзя: игра потеряет соединение.
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
