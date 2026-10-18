package com.example.mlbbregionfilter

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class RegionVpnService : VpnService() {

    companion object {
        const val EXTRA_COUNTRIES = "selected_countries"

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

    private fun startVpn(selectedCountries: List<String>) {

        val builder = Builder()

        builder.setSession("MLBB Region Filter")

        builder.setMtu(VPN_MTU)

        builder.addAddress(
            VPN_ADDRESS,
            VPN_PREFIX
        )

        /*
         * Весь IPv4-трафик направляется в TUN.
         *
         * На следующем уровне мы будем пропускать
         * только трафик Mobile Legends и фильтровать
         * destination IP по выбранным странам.
         */
        builder.addRoute(
            "0.0.0.0",
            0
        )

        /*
         * Пока разрешаем приложению создать VPN.
         *
         * Привязку непосредственно к пакету MLBB
         * добавим следующим этапом, когда определим
         * точный applicationId игры.
         */

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

                val output =
                    FileOutputStream(vpn.fileDescriptor)

                val buffer = ByteArray(32767)

                while (running.get()) {

                    val length = input.read(buffer)

                    if (length <= 0) {
                        continue
                    }

                    /*
                     * Здесь находится TUN-пакет.
                     *
                     * Структура:
                     *
                     * IPv4 header
                     * UDP/TCP header
                     * payload
                     *
                     * В следующей версии здесь будет:
                     *
                     * 1. Определение destination IP.
                     * 2. Определение TCP/UDP.
                     * 3. Проверка страны IP.
                     * 4. ALLOW или DROP.
                     * 5. Реальная пересылка разрешённого пакета.
                     */

                    processPacket(
                        buffer,
                        length,
                        output
                    )
                }

                input.close()
                output.close()

            } catch (_: Exception) {
                /*
                 * VPN был остановлен.
                 */
            }

        }.also {
            it.name = "MLBB-VPN-Worker"
            it.start()
        }
    }

    private fun processPacket(
        packet: ByteArray,
        length: Int,
        output: FileOutputStream
    ) {

        /*
         * Пока ничего не отправляем обратно.
         *
         * Это специально.
         *
         * Мы сначала добавим настоящий network forwarding,
         * чтобы случайно не сломать интернет телефона.
         */
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
