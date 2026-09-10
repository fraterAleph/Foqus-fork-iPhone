package app.foqos.android.blocking

import android.content.Context
import app.foqos.android.blocking.vpn.DomainBlockerVpnService
import app.foqos.android.blocking.vpn.DomainRules
import app.foqos.android.data.db.ProfileEntity

/**
 * Applies a profile's restrictions to the device and takes them off again. This is the Android
 * counterpart of the iOS `AppBlockerUtil`, with three layers instead of one ManagedSettingsStore:
 *
 *  1. the accessibility shield, which covers a blocked app when it comes forward,
 *  2. device-owner suspension, when Foqos was provisioned through ADB, and
 *  3. a local DNS tunnel for domain blocking.
 *
 * Layers 2 and 3 are optional; layer 1 always runs.
 */
class BlockingEngine(private val context: Context) {

    private var suspendedByDeviceOwner: Set<String> = emptySet()

    val isShieldServiceEnabled: Boolean
        get() = AppBlockerAccessibilityService.isRunning

    val isDeviceOwner: Boolean
        get() = DeviceOwnerBlocker.isAvailable(context)

    fun activate(profile: ProfileEntity) {
        val packages = profile.blockedPackages.toSet() - SafePackages.forContext(context)

        BlockingState.update(
            context,
            BlockingState.Snapshot(
                active = true,
                profileId = profile.id,
                profileName = profile.name,
                packages = packages,
                allowMode = profile.enableAllowMode,
                strict = profile.strictModeActive,
                suspended = false,
                temporarilyAllowed = emptySet(),
            ),
        )

        applyDeviceOwner(packages, profile.enableAllowMode)
        applyDomainRules(profile)
    }

    /** Called when a break, a pause or a temporary-access grant opens or closes. */
    fun setSuspended(suspended: Boolean) {
        val snapshot = BlockingState.current(context)
        if (!snapshot.active) return
        BlockingState.update(context, snapshot.copy(suspended = suspended))

        if (suspended) {
            releaseDeviceOwner()
            DomainBlockerVpnService.stop(context)
        } else {
            applyDeviceOwner(snapshot.packages, snapshot.allowMode)
            if (DomainRules.read(context).enabled) DomainBlockerVpnService.start(context)
        }
    }

    /** Lets specific apps through while the rest of the session stays blocked. */
    fun setTemporarilyAllowed(packages: Set<String>) {
        val snapshot = BlockingState.current(context)
        if (!snapshot.active) return
        BlockingState.update(context, snapshot.copy(temporarilyAllowed = packages))

        if (isDeviceOwner) {
            DeviceOwnerBlocker.unsuspend(context, packages)
            suspendedByDeviceOwner = suspendedByDeviceOwner - packages
        }
    }

    fun deactivate() {
        BlockingState.clear(context)
        releaseDeviceOwner()
        DomainRules.clear(context)
        DomainBlockerVpnService.stop(context)
    }

    private fun applyDeviceOwner(packages: Set<String>, allowMode: Boolean) {
        if (!isDeviceOwner) return
        // Allow mode would mean suspending every other installed app, which is a far more
        // destructive operation than the shield performs; the shield handles that case alone.
        if (allowMode) return

        val failed = DeviceOwnerBlocker.suspend(context, packages)
        suspendedByDeviceOwner = packages - failed
    }

    private fun releaseDeviceOwner() {
        if (suspendedByDeviceOwner.isEmpty()) return
        DeviceOwnerBlocker.unsuspend(context, suspendedByDeviceOwner)
        suspendedByDeviceOwner = emptySet()
    }

    private fun applyDomainRules(profile: ProfileEntity) {
        val domains = profile.domains.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val enabled = profile.enableDomainBlocking && domains.isNotEmpty()

        DomainRules.write(
            context,
            DomainRules.Rules(
                enabled = enabled,
                domains = domains.toSet(),
                allowMode = profile.enableAllowModeDomains,
            ),
        )

        if (enabled && DomainBlockerVpnService.consentIntent(context) == null) {
            DomainBlockerVpnService.start(context)
        }
    }
}
