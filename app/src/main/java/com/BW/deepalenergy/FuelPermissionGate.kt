package com.BW.deepalenergy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A permission check never invokes su. Only the explicit confirmation path does. */
internal class FuelPermissionGate(
    private val activity: AppCompatActivity,
    private val onReadAllowed: () -> Unit,
) : AutoCloseable {
    companion object {
        const val EXTRA_CHECK = "com.BW.deepalenergy.CHECK_PERMISSIONS"
        private val runtimePermissions = arrayOf(
            "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_SPEED",
        )
    }

    private var dialog: AlertDialog? = null
    private var busy = false
    private val userId = DumpPermissionCommand.userIdFromUid(Process.myUid())
    private val command get() = DumpPermissionCommand.shell(activity.packageName, userId)
    private val adbCommand get() = DumpPermissionCommand.adb(activity.packageName, userId)
    private val runtimeRequest = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        AppLog.i("PERMISSION", "runtime car result=$result")
        if (busy && !activity.isFinishing && !activity.isDestroyed) grantDumpAfterConsent()
    }

    private fun granted(permission: String) =
        activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun check(promptWhenMissing: Boolean) {
        if (activity.isFinishing || activity.isDestroyed || busy) return
        val dump = granted(DumpPermissionCommand.PERMISSION)
        AppLog.i("PERMISSION", "check package=${activity.packageName} user=$userId DUMP=$dump " +
            runtimePermissions.joinToString { "$it=${granted(it)}" })
        if (dump) {
            dialog?.dismiss()
            onReadAllowed()
            return
        }
        if (!promptWhenMissing || dialog != null) return
        show(AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_perm_title))
            .setMessage(activity.getString(R.string.ui_perm_msg1) +
                activity.getString(R.string.ui_perm_msg2) +
                "$command\n\n" +
                activity.getString(R.string.ui_perm_msg3))
            .setPositiveButton(activity.getString(R.string.ui_confirm)) { _, _ -> requestRuntimeAfterConsent() }
            .setNeutralButton(activity.getString(R.string.ui_show_adb)) { _, _ -> showAdbHelp(activity.getString(R.string.ui_adb_help_intro)) }
            .setNegativeButton(activity.getString(R.string.ui_later)) { _, _ -> readLimited() }
            .setCancelable(false)
            .create())
    }

    private fun requestRuntimeAfterConsent() {
        busy = true
        val missing = runtimePermissions.filterNot(::granted)
        if (missing.isEmpty()) grantDumpAfterConsent()
        else runCatching { runtimeRequest.launch(missing.toTypedArray()) }.onFailure {
            AppLog.w("PERMISSION", "runtime request unavailable: ${it.javaClass.simpleName}")
            // DUMP and normal car runtime permissions are independent.
            grantDumpAfterConsent()
        }
    }

    private fun grantDumpAfterConsent() {
        if (granted(DumpPermissionCommand.PERMISSION)) {
            busy = false
            onReadAllowed()
            return
        }
        val progress = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_requesting_title))
            .setMessage(activity.getString(R.string.ui_requesting_msg))
            .setCancelable(false)
            .create()
        show(progress)
        activity.lifecycleScope.launch {
            try {
                AppLog.i("PERMISSION", "explicit user consent; executing fixed grant: $command")
                val result = withContext(Dispatchers.IO) {
                    try { RootShell.run(command, 15_000) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        ShellResult(-3, "", "${error.javaClass.simpleName}: ${error.message}")
                    }
                }
                val verified = granted(DumpPermissionCommand.PERMISSION)
                AppLog.i("PERMISSION", "grant exit=${result.exitCode} timeout=${result.timedOut} verified=$verified " +
                    "stderr=${result.stderr.take(1000)} stdout=${result.stdout.take(1000)}")
                progress.dismiss()
                busy = false
                if (activity.isFinishing || activity.isDestroyed) return@launch
                if (verified) {
                    Toast.makeText(activity, activity.getString(R.string.ui_perm_ok_start), Toast.LENGTH_LONG).show()
                    onReadAllowed()
                } else {
                    val reason = when {
                        result.timedOut -> activity.getString(R.string.ui_timeout)
                        result.exitCode == -2 -> activity.getString(R.string.ui_no_root)
                        result.exitCode != 0 -> activity.getString(R.string.ui_denied)
                        else -> activity.getString(R.string.ui_cmd_done_no_dump)
                    }
                    showAdbHelp(reason)
                }
            } finally {
                busy = false
                progress.dismiss()
            }
        }
    }

    private fun showAdbHelp(reason: String) {
        show(AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_adb_title))
            .setMessage("$reason\n\n$adbCommand\n\n" +
                activity.getString(R.string.ui_adb_msg))
            .setNeutralButton(activity.getString(R.string.ui_copy_cmd), null)
            .setPositiveButton(activity.getString(R.string.ui_recheck), null)
            .setNegativeButton(activity.getString(R.string.ui_later)) { _, _ -> readLimited() }
            .setCancelable(false)
            .create(), adbHelp = true)
    }

    private fun show(value: AlertDialog, adbHelp: Boolean = false) {
        dialog?.dismiss()
        dialog = value
        value.setOnDismissListener { if (dialog === value) dialog = null }
        value.show()
        if (adbHelp) {
            value.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.ui_clip_label), adbCommand))
                Toast.makeText(activity, activity.getString(R.string.ui_cmd_copied), Toast.LENGTH_SHORT).show()
            }
            value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (granted(DumpPermissionCommand.PERMISSION)) {
                    value.dismiss()
                    onReadAllowed()
                    Toast.makeText(activity, activity.getString(R.string.ui_perm_confirmed), Toast.LENGTH_SHORT).show()
                } else {
                    AppLog.w("PERMISSION", "manual recheck: DUMP still missing")
                    Toast.makeText(activity, activity.getString(R.string.ui_dump_still), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun readLimited() {
        AppLog.w("PERMISSION", "DUMP not granted; continuing with available read-only sources")
        Toast.makeText(activity, activity.getString(R.string.ui_read_limited), Toast.LENGTH_LONG).show()
        onReadAllowed()
    }

    override fun close() {
        dialog?.dismiss()
        dialog = null
    }
}
