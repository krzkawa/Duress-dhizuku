package me.lucky.duress.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.IBinder
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Device policy access delegated to Dhizuku (which acts as Device Owner). */
class DeviceAdminManager(private val ctx: Context) {
    fun isAvailable() = try { Dhizuku.init(ctx) } catch (exc: Exception) { false }

    fun isActive() = isAvailable() && Dhizuku.isPermissionGranted()

    fun requestPermission(callback: (Boolean) -> Unit) {
        if (!isAvailable()) {
            callback(false)
            return
        }
        Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
            override fun onRequestPermission(grantResult: Int) {
                callback(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
            }
        })
    }

    fun wipeData() {
        if (!isActive()) throw SecurityException("Dhizuku permission is not granted")
        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            flags = flags.or(DevicePolicyManager.WIPE_SILENTLY)
        proxiedDpm().wipeData(flags)
    }

    // Route DevicePolicyManager calls through Dhizuku's binder so they run as the device owner.
    private fun proxiedDpm(): DevicePolicyManager {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        HiddenApiBypass.addHiddenApiExemptions("L")
        val field = DevicePolicyManager::class.java.getDeclaredField("mService")
        field.isAccessible = true
        val service = field.get(dpm)
        val binder = service.javaClass.getMethod("asBinder").invoke(service) as IBinder
        val wrapped = Dhizuku.binderWrapper(binder)
        val stub = Class.forName("android.app.admin.IDevicePolicyManager\$Stub")
        field.set(dpm, stub.getMethod("asInterface", IBinder::class.java).invoke(null, wrapped))
        return dpm
    }
}
