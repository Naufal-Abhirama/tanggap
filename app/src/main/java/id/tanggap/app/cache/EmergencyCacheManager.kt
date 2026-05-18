package id.tanggap.app.cache

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Monitors battery level and notifies via callback when status changes.
 * Register/unregister mengikuti lifecycle Activity/Composable.
 *
 * Cara pakai di MainActivity (LaunchedEffect / DisposableEffect):
 *   val cacheManager = remember { EmergencyCacheManager(context) }
 *   DisposableEffect(Unit) {
 *       cacheManager.startMonitoring { level, critical -> isBatteryCritical = critical }
 *       onDispose { cacheManager.stopMonitoring() }
 *   }
 */
class EmergencyCacheManager(private val context: Context) {

    private var batteryReceiver: BroadcastReceiver? = null

    fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun isBatteryCritical(): Boolean = getBatteryLevel() <= 20

    /**
     * Daftar BroadcastReceiver untuk update baterai real-time.
     * onUpdate dipanggil setiap kali level berubah.
     */
    fun startMonitoring(onUpdate: (level: Int, isCritical: Boolean) -> Unit) {
        if (batteryReceiver != null) return   // sudah terdaftar

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct   = if (scale > 0) (level * 100) / scale else getBatteryLevel()
                onUpdate(pct, pct <= 20)
            }
        }

        context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
    }

    fun stopMonitoring() {
        batteryReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        batteryReceiver = null
    }
}
