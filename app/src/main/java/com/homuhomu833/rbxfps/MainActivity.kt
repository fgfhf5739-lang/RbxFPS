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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import rikka.shizuku.Shizuku

private data class Ratio(val label: String, val num: Int, val den: Int)

class MainActivity : AppCompatActivity() {

    private companion object {
        const val FILE = "/data/local/tmp/ClientAppSettings.json"
        const val ROBLOX = "com.roblox.client"
        const val FLAG = "DFIntTaskSchedulerTargetFps"
        val FPS_VALUES = listOf(45, 60, 90, 120, 144, 165, 185)
        const val DEFAULT_FPS = 60
        const val RESET_DISPLAY = "wm size reset; wm density reset"
        val RATIOS = listOf(
            Ratio("100% (Default)", 1, 1),
            Ratio("75%", 3, 4),
            Ratio("66.6% (2/3)", 2, 3),
            Ratio("50%", 1, 2),
            Ratio("33.3% (1/3)", 1, 3),
            Ratio("25%", 1, 4)
        )
    }

    private lateinit var modeToggle: MaterialButtonToggleGroup
    private lateinit var ratioLayout: View
    private lateinit var pLayout: View
    private lateinit var pLabel: TextView
    private lateinit var pSlider: Slider
    private lateinit var resDropdown: MaterialAutoCompleteTextView
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

        modeToggle = findViewById(R.id.modeToggle)
        ratioLayout = findViewById(R.id.ratioLayout)
        pLayout = findViewById(R.id.pLayout)
        pLabel = findViewById(R.id.pLabel)
        pSlider = findViewById(R.id.pSlider)
        fpsDropdown = findViewById(R.id.fpsDropdown)
        resDropdown = findViewById(R.id.resDropdown)
        logView = findViewById(R.id.logView)

        fpsDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                FPS_VALUES.map { "$it FPS" }
            )
        )
        fpsDropdown.setText("$DEFAULT_FPS FPS", false)

        resDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                RATIOS.map { it.label }
            )
        )
        resDropdown.setText(RATIOS[0].label, false)

        pSlider.addOnChangeListener { _, value, _ -> pLabel.text = "${value.toInt()}p" }

        modeToggle.addOnButtonCheckedListener { _, _, _ ->
            val ratioMode = isRatioMode()
            ratioLayout.visibility = if (ratioMode) View.VISIBLE else View.GONE
            pLayout.visibility = if (ratioMode) View.GONE else View.VISIBLE
        }

        Shizuku.addRequestPermissionResultListener(permListener)

        findViewById<View>(R.id.btnApply).setOnClickListener {
            val fps = selectedFps()
            val json = "{\"$FLAG\":$fps}"
            val (resLabel, resCmd) = if (isRatioMode()) {
                val r = selectedRatio()
                r.label to displayCmd("${r.num}", "${r.den}")
            } else {
                val p = pSlider.value.toInt()
                "${p}p" to displayCmd("$p", "\$SH")
            }
            exec(
                "Apply $fps FPS, $resLabel",
                "rm -f $FILE; printf '%s' '$json' > $FILE && chmod 644 $FILE; " +
                    "$resCmd; am force-stop $ROBLOX; cat $FILE"
            )
        }
        findViewById<View>(R.id.btnRemove).setOnClickListener {
            exec(
                "Remove override",
                "rm -f $FILE; $RESET_DISPLAY; am force-stop $ROBLOX; echo removed"
            )
        }
        findViewById<View>(R.id.btnStatus).setOnClickListener {
            exec("Status", "ls -l $FILE 2>&1; cat $FILE 2>&1; echo; wm size; wm density")
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun isRatioMode(): Boolean =
        modeToggle.checkedButtonId == R.id.btnModeRatio

    private fun selectedFps(): Int =
        fpsDropdown.text.toString().filter { it.isDigit() }.toIntOrNull() ?: DEFAULT_FPS

    private fun selectedRatio(): Ratio =
        RATIOS.firstOrNull { it.label == resDropdown.text.toString() } ?: RATIOS[0]

    /**
     * Scales the physical screen by N/M (size and density). If N >= M, resets to default.
     * For "p" mode, N = target p and M = $SH (the physical short side in pixels).
     */
    private fun displayCmd(n: String, m: String): String =
        "S=\$(wm size | grep -i physical | sed 's/.*: //'); " +
            "W=\${S%x*}; H=\${S#*x}; " +
            "DN=\$(wm density | grep -i physical | sed 's/.*: //'); " +
            "SH=\$((W<H?W:H)); N=$n; M=$m; " +
            "if [ \$N -ge \$M ]; then $RESET_DISPLAY; " +
            "else wm size \$((W*N/M/2*2))x\$((H*N/M/2*2)); wm density \$((DN*N/M)); fi"

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
