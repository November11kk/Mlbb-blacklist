package com.example.mlbbregionfilter

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class RegionVpnService : VpnService() {

    companion object {
        const val EXTRA_COUNTRIES = "selected_countries"
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val countries =
            intent?.getStringArrayListExtra(EXTRA_COUNTRIES)
                ?: arrayListOf()

        startVpn(countries)

        return START_STICKY
    }

    private fun startVpn(countries: List<String>) {

        vpnInterface?.close()

        val builder = Builder()

        builder.setSession("MLBB Region Filter")

        builder.setMtu(1500)

        builder.addAddress(
            "10.10.0.2",
            32
        )

        builder.addRoute(
            "0.0.0.0",
            0
        )

        /*
         * В будущем здесь будет:
         *
         * 1. Ограничение VPN только для MLBB.
         * 2. Получение destination IP каждого пакета.
         * 3. Определение страны IP.
         * 4. Проверка выбранных стран.
         * 5. DROP для запрещённых IP.
         * 6. FORWARD для разрешённых.
         */

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            stopSelf()
            return
        }

        Thread {
            processPackets()
        }.start()
    }

    private fun processPackets() {

        val vpn = vpnInterface ?: return

        val input =
            FileInputStream(vpn.fileDescriptor)

        val output =
            FileOutputStream(vpn.fileDescriptor)

        val buffer = ByteArray(32767)

        try {

            while (!Thread.currentThread().isInterrupted) {

                val length = input.read(buffer)

                if (length <= 0) {
                    continue
                }

                /*
                 * Пока только читаем пакеты.
                 *
                 * Нельзя просто отправить их обратно
                 * в этот же TUN-интерфейс.
                 *
                 * На следующем этапе здесь будет
                 * настоящий packet forwarding.
                 */
            }

        } catch (_: Exception) {
            // VPN остановлен
        } finally {
            input.close()
            output.close()
        }
    }

    override fun onDestroy() {

        vpnInterface?.close()
        vpnInterface = null

        super.onDestroy()
    }
}
