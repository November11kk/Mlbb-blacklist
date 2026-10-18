package com.example.mlbbregionfilter

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

class RegionVpnService : VpnService() {

    companion object {
        const val EXTRA_COUNTRIES = "selected_countries"

        private const val MLBB_PACKAGE = "com.mobile.legends"

        private const val VPN_ADDRESS = "10.10.0.2"
        private const val VPN_PREFIX = 32
        private const val VPN_MTU = 1500
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    private var workerThread: Thread? = null

    private val running = AtomicBoolean(false)

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val selectedCountries =
            intent?.getStringArrayListExtra(EXTRA_COUNTRIES)
                ?: arrayListOf()

        stopVpn()

        startVpn(selectedCountries)

        return START_STICKY
    }

    private fun startVpn(
        selectedCountries: List<String>
    ) {

        val builder = Builder()

        builder.setSession("MLBB Region Filter")

        builder.setMtu(VPN_MTU)

        /*
         * Только Mobile Legends использует этот VPN.
         *
         * Остальные приложения телефона VPN не используют.
         */
        try {
            builder.addAllowedApplication(MLBB_PACKAGE)
        } catch (_: Exception) {
            stopSelf()
            return
        }

        builder.addAddress(
            VPN_ADDRESS,
            VPN_PREFIX
        )

        /*
         * Весь IPv4-трафик Mobile Legends
         * направляется в VPN-интерфейс.
         */
        builder.addRoute(
            "0.0.0.0",
            0
        )

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            stopSelf()
            return
        }

        running.set(true)

        workerThread = Thread {

            val vpn = vpnInterface ?: return@Thread

            try {

                val input =
                    FileInputStream(vpn.fileDescriptor)

                val buffer = ByteArray(32767)

                while (running.get()) {

                    val length = input.read(buffer)

                    if (length <= 0) {
                        continue
                    }

                    /*
                     * Здесь мы получаем IP-пакет Mobile Legends.
                     *
                     * Следующий этап:
                     *
                     * 1. Определяем destination IP.
                     * 2. Определяем UDP/TCP.
                     * 3. Сверяем IP со странами.
                     * 4. Разрешаем или блокируем пакет.
                     * 5. Передаём разрешённый трафик
                     *    через сетевой движок.
                     */
                }

                input.close()

            } catch (_: Exception) {
                // VPN остановлен.
            }

        }.also {
            it.name = "MLBB-VPN-Worker"
            it.start()
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
