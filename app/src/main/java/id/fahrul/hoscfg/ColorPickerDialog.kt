package id.fahrul.hoscfg

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.*

class ColorPickerDialog(
    private val ctx: Context,
    private val currentColor: Int,
    private val onPicked: (Int) -> Unit
) {
    private var selectedColor = currentColor

    fun show() {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val wheel = ColorWheelView(ctx, selectedColor)
        wheel.layoutParams = LinearLayout.LayoutParams(400, 400).apply {
            gravity = android.view.Gravity.CENTER
        }
        root.addView(wheel)

        val hexRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        hexRow.addView(TextView(ctx).apply { text = "Hex: " })
        val hexInput = EditText(ctx).apply {
            setText(String.format("#%06X", selectedColor and 0xFFFFFF))
            setSingleLine()
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        hexRow.addView(hexInput)
        root.addView(hexRow)

        val preview = View(ctx).apply {
            setBackgroundColor(selectedColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48
            ).apply { topMargin = 16 }
        }
        root.addView(preview)

        wheel.onColorChanged = { color ->
            selectedColor = (Color.alpha(selectedColor) shl 24) or (color and 0xFFFFFF)
            hexInput.setText(String.format("#%06X", color and 0xFFFFFF))
            preview.setBackgroundColor(selectedColor)
        }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trimStart('#') ?: return
                if (text.length == 6) try {
                    val rgb = Integer.parseInt(text, 16)
                    selectedColor = (Color.alpha(selectedColor) shl 24) or (rgb and 0xFFFFFF)
                    preview.setBackgroundColor(selectedColor)
                    wheel.setColor(rgb)
                } catch (_: NumberFormatException) {}
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        AlertDialog.Builder(ctx)
            .setTitle("Drawer Background Color")
            .setView(root)
            .setPositiveButton("Apply") { _, _ -> onPicked(selectedColor) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class ColorWheelView(ctx: Context, private var color: Int) : View(ctx) {

    var onColorChanged: ((Int) -> Unit)? = null

    private val bmp: Bitmap
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val selectorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 255, 255, 255)
    }

    init {
        bmp = createWheel(400)
    }

    fun setColor(rgb: Int) {
        color = rgb
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy) - 10f
        canvas.drawBitmap(bmp, null, RectF(cx - r, cy - r, cx + r, cy + r), null)

        val hsv = floatArrayOf(0f, 0f, 0f)
        Color.colorToHSV(color, hsv)
        val angle = Math.toRadians(hsv[0].toDouble()).toFloat()
        val selR = r * hsv[1]
        val sx = cx + selR * kotlin.math.cos(angle)
        val sy = cy + selR * kotlin.math.sin(angle)
        canvas.drawCircle(sx, sy, 14f, selectorFill)
        canvas.drawCircle(sx, sy, 14f, selectorPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(cx, cy) - 10f

            val dx = event.x - cx
            val dy = event.y - cy
            val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            val sat = (dist / r).coerceIn(0f, 1f)

            // Angle: 0°=right, 90°=down (+Y on screen), 270°=up (-Y on screen)
            // Map to hue: 0°→0 (red), clockwise wrap
            var deg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (deg < 0) deg += 360f

            val hsv = floatArrayOf(deg, sat, 1f)
            color = Color.HSVToColor(hsv)
            onColorChanged?.invoke(color)
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun createWheel(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - cx)
                val dy = (y - cy)
                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist <= r) {
                    val sat = dist / r
                    var deg = Math.toDegrees(
                        kotlin.math.atan2(dy.toDouble(), dx.toDouble())
                    ).toFloat()
                    if (deg < 0) deg += 360f
                    bmp.setPixel(x, y, Color.HSVToColor(floatArrayOf(deg, sat, 1f)))
                }
            }
        }
        return bmp
    }
}
