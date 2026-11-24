package com.BW.deepalenergy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private val exportData = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { LocalFuelDataSource.exportTo(this@DiagnosticsActivity, uri) }
            }
            Toast.makeText(
                this@DiagnosticsActivity,
                result.fold({ getString(R.string.ui_exported_points, it) }, { getString(R.string.ui_export_failed, it.message) }),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    private val importData = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_import_title))
            .setMessage(getString(R.string.ui_import_msg))
            .setPositiveButton(getString(R.string.ui_import_confirm)) { _, _ ->
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { LocalFuelDataSource.importFrom(this@DiagnosticsActivity, uri) }
                    }
                    result.onFailure { AppLog.e("DATA", "JSON import failed", it) }
                    Toast.makeText(
                        this@DiagnosticsActivity,
                        result.fold({ getString(R.string.ui_imported_points, it) }, { getString(R.string.ui_import_failed, it.message) }),
                        Toast.LENGTH_LONG,
                    ).show()
                    refreshLog()
                }
            }
            .setNegativeButton(getString(R.string.ui_cancel), null)
            .show()
    }
    private val chooseData = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_use_existing_title))
            .setMessage(getString(R.string.ui_use_existing_msg))
            .setPositiveButton(getString(R.string.ui_use_this)) { _, _ ->
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { LocalFuelDataSource.selectExisting(this@DiagnosticsActivity, uri) }
                    }
                    Toast.makeText(this@DiagnosticsActivity, result.fold({ getString(R.string.ui_read_points, it) }, { getString(R.string.ui_data_unusable, it.message) }), Toast.LENGTH_LONG).show()
                    refreshLog()
                }
            }.setNegativeButton(getString(R.string.ui_cancel), null).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        AppLog.initialize(this)
        AppLog.i("UI", "diagnostics opened")
        setContentView(buildContent())
        refreshLog()
    }

    private fun buildContent(): View {
        val typeface = ResourcesCompat.getFont(this, R.font.lynkco_type_medium)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 28, 39))
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            TextView(this).apply {
                text = getString(R.string.ui_diag_title)
                textSize = 28f
                setTextColor(Color.WHITE)
                this.typeface = typeface
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            this.typeface = typeface
            setOnClickListener { action() }
        }

        titleRow.addView(actionButton(getString(R.string.ui_refresh), ::refreshLog))
        titleRow.addView(actionButton(getString(R.string.ui_copy_log)) {
            copyToClipboard(getString(R.string.ui_clip_log_label), AppLog.readText())
        })
        titleRow.addView(actionButton(getString(R.string.ui_copy_path)) {
            copyToClipboard(getString(R.string.ui_clip_path_label), AppLog.path())
        })
        titleRow.addView(actionButton(getString(R.string.ui_clear)) {
            AppLog.clear()
            refreshLog()
        })
        titleRow.addView(actionButton(getString(R.string.ui_back)) { finish() })
        root.addView(titleRow)

        val dataRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dataRow.addView(actionButton(getString(R.string.ui_export_json)) {
            runCatching { exportData.launch("autoenergy-data-${System.currentTimeMillis()}.json") }
                .onFailure { Toast.makeText(this@DiagnosticsActivity, getString(R.string.ui_cannot_open_export), Toast.LENGTH_SHORT).show() }
        })
        dataRow.addView(actionButton(getString(R.string.ui_import_json)) {
            runCatching { importData.launch(arrayOf("application/json", "text/json", "*/*")) }
                .onFailure { Toast.makeText(this@DiagnosticsActivity, getString(R.string.ui_cannot_open_picker), Toast.LENGTH_SHORT).show() }
        })
        dataRow.addView(actionButton(getString(R.string.ui_check_permissions)) {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra(FuelPermissionGate.EXTRA_CHECK, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        })
        dataRow.addView(actionButton(getString(R.string.ui_save_now)) {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { LocalFuelDataSource.read(this@DiagnosticsActivity); LocalFuelDataSource.flush(force = true) } }
                Toast.makeText(this@DiagnosticsActivity, if (result.isSuccess) getString(R.string.ui_saved_json) else getString(R.string.ui_save_failed, result.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
                refreshLog()
            }
        })
        dataRow.addView(actionButton(getString(R.string.ui_deepal_panel)) {
            startActivity(Intent(this, com.BW.deepalenergy.energy.DeepalPanelActivity::class.java))
        })
        dataRow.addView(actionButton(getString(R.string.ui_choose_data)) {
            runCatching { chooseData.launch("application/json") }.onFailure { Toast.makeText(this, getString(R.string.ui_choose_via_files), Toast.LENGTH_LONG).show() }
        })
        dataRow.addView(TextView(this).apply {
            text = getString(R.string.ui_diag_auto_load)
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(12), 0, 0)
        })
        root.addView(dataRow)

        root.addView(TextView(this).apply {
            text = getString(R.string.ui_diag_hint, AppLog.path())
            textSize = 15f
            setTextColor(Color.rgb(141, 183, 204))
            setPadding(0, dp(8), 0, dp(12))
            this.typeface = typeface
        })

        logText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(220, 232, 238))
            setTextIsSelectable(true)
            setPadding(dp(18), dp(14), dp(18), dp(20))
            this.typeface = typeface
        }
        val horizontal = HorizontalScrollView(this).apply {
            isFillViewport = true
            addView(logText)
        }
        logScroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(12, 40, 53))
            addView(horizontal)
        }
        root.addView(
            logScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return root
    }

    private fun refreshLog() {
        if (!::logText.isInitialized) return
        logText.text = AppLog.readText()
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, getString(R.string.ui_copied), Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
