package id.fahrul.hoscfg

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Config.syncToRemote()

        val seekAlpha = findViewById<SeekBar>(R.id.seek_alpha)
        val alphaText = findViewById<android.widget.TextView>(R.id.text_alpha_value)
        val colorPreview = findViewById<View>(R.id.color_preview)
        val colorHex = findViewById<android.widget.TextView>(R.id.text_color_hex)
        val colorRow = findViewById<View>(R.id.row_color_picker)

        val labelPreview = findViewById<View>(R.id.label_preview)
        val labelHex = findViewById<android.widget.TextView>(R.id.text_label_hex)
        val labelRow = findViewById<View>(R.id.row_label_color)

        val noSimToggle = findViewById<Switch>(R.id.toggle_hide_no_sim)
        val restartBtn = findViewById<MaterialButton>(R.id.btn_restart)
        val restartSysUiBtn = findViewById<MaterialButton>(R.id.btn_restart_systemui)

        // Hide No SIM Icon
        noSimToggle.isChecked = Config.getBool(Config.KEY_HIDE_NO_SIM, false)
        noSimToggle.setOnCheckedChangeListener { _, checked ->
            Config.setBool(Config.KEY_HIDE_NO_SIM, checked)
        }

        // Background color
        var bgColor = Config.getInt(Config.KEY_BG_COLOR, Color.BLACK)
        val savedAlpha = Config.getInt(Config.KEY_BG_ALPHA, 255)
        updatePreview(colorPreview, colorHex, bgColor)

        seekAlpha.progress = savedAlpha
        alphaText.text = "Alpha: $savedAlpha"
        seekAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                alphaText.text = "Alpha: $value"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                Config.setInt(Config.KEY_BG_ALPHA, sb?.progress ?: 255)
            }
        })

        colorRow.setOnClickListener {
            ColorPickerDialog(this, bgColor) { picked ->
                bgColor = picked
                updatePreview(colorPreview, colorHex, picked)
                Config.setInt(Config.KEY_BG_COLOR, picked)
            }.show()
        }

        // Label color
        var labelColor = Config.getInt(Config.KEY_LABEL_COLOR, Color.TRANSPARENT)
        updateLabelPreview(labelPreview, labelHex, labelColor)

        labelRow.setOnClickListener {
            val current = if (labelColor == Color.TRANSPARENT) Color.WHITE else labelColor
            ColorPickerDialog(this, current) { picked ->
                labelColor = picked
                updateLabelPreview(labelPreview, labelHex, picked)
                Config.setInt(Config.KEY_LABEL_COLOR, picked)
            }.show()
        }

        restartBtn.setOnClickListener { Config.restartLauncher(this) }
        restartSysUiBtn.setOnClickListener { Config.restartSystemUi(this) }
    }

    private fun updatePreview(v: View, tv: android.widget.TextView, color: Int) {
        v.setBackgroundColor(color)
        tv.text = String.format("#%06X", color and 0xFFFFFF)
    }

    private fun updateLabelPreview(v: View, tv: android.widget.TextView, color: Int) {
        if (color == Color.TRANSPARENT) {
            v.setBackgroundColor(Color.WHITE)
            tv.text = "Default (auto)"
        } else {
            v.setBackgroundColor(color)
            tv.text = String.format("#%06X", color and 0xFFFFFF)
        }
    }
}
