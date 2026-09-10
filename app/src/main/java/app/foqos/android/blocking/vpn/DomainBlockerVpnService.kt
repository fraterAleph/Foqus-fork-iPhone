package app.foqos.android.blocking.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import app.foqos.android.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

/**
 * Website blocking, the only way it can be done on Android without root: a local VPN that
 * answers DNS.
 *
 * The tunnel routes exactly one address — the fake DNS server handed to the system — so normal
 * traffic never enters this process. A query for a blocked host gets NXDOMAIN; everything else
 * is forwarded upstream unchanged.
 *
 * Two limits are worth being honest about: an app that ships its own DNS-over-HTTPS resolver
 * (Chrome's secure DNS, for one) bypasses this entirely, and only one VPN can be active per
 * device, so this conflicts with a user's real VPN.
 */
class DomainBlockerVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        if (running) return
        val rules = DomainRules.read(this)
        if (!rules.enabled) {
            stopSelf()
            return
        }

        val descriptor = runCatching {
            Builder()
                .setSession("Foqos")
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .addRoute(DNS_ADDRESS, 32)
                .setMtu(MTU)
                .setConfigureIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .establish()
        }.getOrElse {
            Log.w(TAG, "Could not establish the DNS tunnel", it)
            null
        }

        if (descriptor == null) {
            stopSelf()
            return
        }

        tunnel = descriptor
        running = true
        isActive = true
        worker = thread(name = "foqos-dns", isDaemon = true) { pump(descriptor) }
    }

    private fun pump(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(MTU)

        val upstream = DatagramSocket()
        if (!protect(upstream)) {
            Log.w(TAG, "Upstream socket is not protected, aborting to avoid a resolution loop")
            upstream.close()
            teardown()
            return
        }
        upstream.soTimeout = UPSTREAM_TIMEOUT_MS

        try {
            while (running) {
                val read = input.read(buffer)
                if (read <= 0) continue

                val datagram = Ipv4Udp.parse(buffer, read) ?: continue
                if (datagram.destinationPort != DNS_PORT) continue

                val response = resolve(datagram.payload, upstream) ?: continue
                output.write(Ipv4Udp.buildResponse(datagram, response))
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "DNS pump stopped", e)
        } finally {
            runCatching { upstream.close() }
            runCatching { input.close() }
            runCatching { output.close() }
        }
    }

    /** NXDOMAIN for a blocked name, otherwise the upstream resolver's own answer. */
    private fun resolve(query: ByteArray, upstream: DatagramSocket): ByteArray? {
        val rules = DomainRules.read(this)
        val name = DnsMessage.queryName(query)

        if (name != null && rules.blocks(name)) {
            return DnsMessage.nxDomain(query)
        }

        return runCatching {
            val server = InetSocketAddress(InetAddress.getByName(rules.upstreamDns), DNS_PORT)
            upstream.send(DatagramPacket(query, query.size, server))
            val reply = ByteArray(MTU)
            val packet = DatagramPacket(reply, reply.size)
            upstream.receive(packet)
            reply.copyOf(packet.length)
        }.getOrElse {
            // If the upstream is unreachable, failing closed on a blocked-list profile would be
            // wrong: return SERVFAIL-shaped NXDOMAIN only for names we were asked to block.
            null
        }
    }

    private fun teardown() {
        running = false
        isActive = false
        worker?.interrupt()
        worker = null
        runCatching { tunnel?.close() }
        tunnel = null
    }

    override fun onRevoke() {
        teardown()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FoqosDns"
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val DNS_ADDRESS = "10.111.222.2"
        private const val DNS_PORT = 53
        private const val MTU = 1500
        private const val UPSTREAM_TIMEOUT_MS = 4000
        const val ACTION_STOP = "app.foqos.android.STOP_DNS"

        @Volatile
        var isActive: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, DomainBlockerVpnService::class.java)
            // Called from receivers too, where a background start can be refused outright.
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DomainBlockerVpnService::class.java)
                .setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        /** Null when the user has already granted the VPN consent. */
        fun consentIntent(context: Context): Intent? = VpnService.prepare(context)
    }
}
