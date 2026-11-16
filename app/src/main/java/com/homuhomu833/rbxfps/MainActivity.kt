package com.homuhomu833.rbxfps

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private companion object {
        const val FILE = "/data/local/tmp/ClientAppSettings.json"
        const val ROBLOX = "com.roblox.client"
        const val FLAG = "DFIntTaskSchedulerTargetFps"
        val FPS_VALUES = listOf(60, 90, 120)
        const val DEFAULT_FPS = 60
    }

    private lateinit var fpsDropdown: MaterialAutoCompleteTextView
    private lateinit var logView: TextView

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        log(
            if (result == PackageManager.PERMISSION_GRANTED) "Shizuku permission granted. Tap the button again."
            else "Shizuku permission denied."
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        fpsDropdown = findViewById(R.id.fpsDropdown)
        logView = findViewById(R.id.logView)

        fpsDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                FPS_VALUES.map { "$it FPS" }
            )
        )
        fpsDropdown.setText("$DEFAULT_FPS FPS", false)

        Shizuku.addRequestPermissionResultListener(permListener)

        findViewById<View>(R.id.btnApply).setOnClickListener {
            val fps = selectedFps()
            val json = "{\"$FLAG\":$fps}"
            exec(
                "Apply $fps FPS",
                "rm -f $FILE; printf '%s' '$json' > $FILE && chmod 644 $FILE && am force-stop $ROBLOX && cat $FILE"
            )
        }
        findViewById<View>(R.id.btnRemove).setOnClickListener {
            exec("Remove override", "rm -f $FILE && am force-stop $ROBLOX && echo removed")
        }
        findViewById<View>(R.id.btnStatus).setOnClickListener {
            exec("File status", "ls -l $FILE 2>&1; cat $FILE 2>&1")
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun selectedFps(): Int =
        fpsDropdown.text.toString().filter { it.isDigit() }.toIntOrNull() ?: DEFAULT_FPS

    /** Returns true if Shizuku is usable; otherwise shows why. */
    private fun ready(): Boolean {
        if (!Shizuku.pingBinder()) {
            log("Shizuku is not running. Start it first in the Shizuku app.")
            return false
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1)
            log("Requesting Shizuku permission...")
            return false
        }
        return true
    }

    private fun exec(label: String, cmd: String) {
        log("$label...")
        Thread {
            if (!ready()) return@Thread
            val r = Sh.run(cmd)
            log("$label: ${if (r.ok) "OK" else "FAILED"}\n${r.out}")
        }.start()
    }

    private fun log(msg: String) {
        runOnUiThread { logView.text = msg }
    }
}
