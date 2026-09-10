package app.foqos.android.blocking.vpn

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Just enough IPv4/UDP to answer DNS queries inside the tun device. The VPN only routes the
 * fake DNS server address, so these are the only packets that ever reach us.
 */
object Ipv4Udp {

    const val PROTOCOL_UDP = 17
    private const val IPV4_MIN_HEADER = 20
    private const val UDP_HEADER = 8

    data class UdpDatagram(
        val sourceAddress: ByteArray,
        val destinationAddress: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /** Returns null for anything that is not an IPv4 UDP datagram we can handle. */
    fun parse(packet: ByteArray, length: Int): UdpDatagram? {
        if (length < IPV4_MIN_HEADER + UDP_HEADER) return null

        val versionAndIhl = packet[0].toInt() and 0xFF
        if ((versionAndIhl shr 4) != 4) return null

        val headerLength = (versionAndIhl and 0x0F) * 4
        if (headerLength < IPV4_MIN_HEADER || length < headerLength + UDP_HEADER) return null
        if ((packet[9].toInt() and 0xFF) != PROTOCOL_UDP) return null

        val source = packet.copyOfRange(12, 16)
        val destination = packet.copyOfRange(16, 20)

        val udp = ByteBuffer.wrap(packet, headerLength, length - headerLength)
            .order(ByteOrder.BIG_ENDIAN)
        val sourcePort = udp.short.toInt() and 0xFFFF
        val destinationPort = udp.short.toInt() and 0xFFFF
        val udpLength = udp.short.toInt() and 0xFFFF
        udp.short // checksum, not validated: the tun already delivered a well-formed packet

        val payloadLength = (udpLength - UDP_HEADER).coerceIn(0, udp.remaining())
        val payload = ByteArray(payloadLength)
        udp.get(payload)

        return UdpDatagram(source, destination, sourcePort, destinationPort, payload)
    }

    /** Builds the reply datagram: the request's addresses and ports, swapped. */
    fun buildResponse(request: UdpDatagram, payload: ByteArray): ByteArray {
        val totalLength = IPV4_MIN_HEADER + UDP_HEADER + payload.size
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)

        buffer.put((4 shl 4 or 5).toByte())      // IPv4, 5 * 4 byte header
        buffer.put(0)                            // DSCP / ECN
        buffer.putShort(totalLength.toShort())
        buffer.putShort(0)                       // identification
        buffer.putShort(0x4000.toShort())        // don't fragment
        buffer.put(64)                           // TTL
        buffer.put(PROTOCOL_UDP.toByte())
        buffer.putShort(0)                       // checksum placeholder
        buffer.put(request.destinationAddress)   // reply comes from the queried server
        buffer.put(request.sourceAddress)

        val ipChecksum = checksum(buffer.array(), 0, IPV4_MIN_HEADER)
        buffer.putShort(10, ipChecksum.toShort())

        buffer.putShort(request.destinationPort.toShort())
        buffer.putShort(request.sourcePort.toShort())
        buffer.putShort((UDP_HEADER + payload.size).toShort())
        buffer.putShort(0)                       // UDP checksum placeholder
        buffer.put(payload)

        val udpChecksum = udpChecksum(
            buffer.array(),
            sourceAddress = request.destinationAddress,
            destinationAddress = request.sourceAddress,
            udpOffset = IPV4_MIN_HEADER,
            udpLength = UDP_HEADER + payload.size,
        )
        buffer.putShort(IPV4_MIN_HEADER + 6, udpChecksum.toShort())

        return buffer.array()
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun udpChecksum(
        packet: ByteArray,
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        udpOffset: Int,
        udpLength: Int,
    ): Int {
        var sum = 0L
        for (address in listOf(sourceAddress, destinationAddress)) {
            var i = 0
            while (i + 1 < address.size) {
                sum += ((address[i].toInt() and 0xFF) shl 8) or (address[i + 1].toInt() and 0xFF)
                i += 2
            }
        }
        sum += PROTOCOL_UDP.toLong()
        sum += udpLength.toLong()

        var i = udpOffset
        val end = udpOffset + udpLength
        while (i + 1 < end) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (packet[i].toInt() and 0xFF) shl 8

        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val result = (sum.inv() and 0xFFFF).toInt()
        // A zero checksum means "not computed" in UDP, so it is transmitted as all ones.
        return if (result == 0) 0xFFFF else result
    }
}
