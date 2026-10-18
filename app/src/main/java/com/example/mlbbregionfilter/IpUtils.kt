package com.example.mlbbregionfilter

object IpUtils {

    fun ipv4ToLong(
        b0: Int,
        b1: Int,
        b2: Int,
        b3: Int
    ): Long {

        return ((b0 and 0xFF).toLong() shl 24) or
                ((b1 and 0xFF).toLong() shl 16) or
                ((b2 and 0xFF).toLong() shl 8) or
                (b3 and 0xFF).toLong()
    }

    fun parseIpv4Address(
        packet: ByteArray,
        offset: Int = 16
    ): Long {

        if (packet.size < offset + 4) {
            return 0
        }

        return ipv4ToLong(
            packet[offset].toInt(),
            packet[offset + 1].toInt(),
            packet[offset + 2].toInt(),
            packet[offset + 3].toInt()
        )
    }

    fun cidrContains(
        ip: Long,
        cidr: String
    ): Boolean {

        val parts = cidr.split("/")

        if (parts.size != 2) {
            return false
        }

        val address = parts[0]
        val prefix = parts[1].toIntOrNull()
            ?: return false

        if (prefix !in 0..32) {
            return false
        }

        val addressParts = address.split(".")

        if (addressParts.size != 4) {
            return false
        }

        val network = ipv4ToLong(
            addressParts[0].toIntOrNull() ?: return 0,
            addressParts[1].toIntOrNull() ?: return 0,
            addressParts[2].toIntOrNull() ?: return 0,
            addressParts[3].toIntOrNull() ?: return 0
        )

        if (prefix == 0) {
            return true
        }

        val mask =
            (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL

        return (ip and mask) == (network and mask)
    }

    fun isIpInRanges(
        ip: Long,
        ranges: List<String>
    ): Boolean {

        for (cidr in ranges) {

            if (cidrContains(ip, cidr)) {
                return true
            }
        }

        return false
    }
}
