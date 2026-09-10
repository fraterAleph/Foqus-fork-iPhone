package app.foqos.android.blocking

import android.app.admin.DeviceAdminReceiver

/** Present so Foqos can be provisioned as device owner. It holds no policy of its own. */
class FoqosDeviceAdminReceiver : DeviceAdminReceiver()
