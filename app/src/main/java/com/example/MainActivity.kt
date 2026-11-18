package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.VideoEditorScreen
import com.example.ui.theme.AfterMotionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.install(this)
        val report = CrashReport.pending(this)
        // Build 228: native crash logger (after pending() so a fresh file is not read as "old").
        com.example.engine.NativeCrash.installTo(java.io.File(filesDir, CrashReport.NATIVE_FILE).absolutePath)
        if (report != null) showCrashReport(report) else startApp()
    }

    private fun startApp() {
        com.example.engine.StickerLibrary.init(applicationContext)
        com.example.engine.CutoutTrace.incomplete(applicationContext)?.let {
            android.widget.Toast.makeText(this, "Last cutout stopped at: $it", android.widget.Toast.LENGTH_LONG).show()
        }
        enableEdgeToEdge()
        setContent {
            AfterMotionTheme {
                VideoEditorScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }

    /** The last run crashed: show the error text first (plain Android views) so it can be copied and sent. */
    private fun showCrashReport(report: String) {
        val text = android.widget.TextView(this).apply {
            this.text = report
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 20, 40, 20)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(text) }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("The app crashed last time")
            .setView(scroll)
            .setCancelable(false)
            .setPositiveButton("Open app", null)
            .setNeutralButton("Copy", null)
            .create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash", report))
            android.widget.Toast.makeText(this, "Copied", android.widget.Toast.LENGTH_SHORT).show()
        }
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            CrashReport.clear(this)
            dialog.dismiss()
            startApp()
        }
    }
}
