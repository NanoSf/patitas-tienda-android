package co.patitas.tienda.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import co.patitas.tienda.R
import co.patitas.tienda.data.SalesPoint
import co.patitas.tienda.util.Format

/**
 * Vista personalizada que dibuja un gráfico de barras con Canvas.
 * Sus colores y radio se configuran desde XML con los atributos declarados en
 * res/values/attrs.xml (chartBarColor, chartHighlightColor, chartLabelColor, chartCornerRadius).
 * La última barra (periodo actual) se resalta con chartHighlightColor.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var cornerRadius: Float
    private var points: List<SalesPoint> = emptyList()

    init {
        val density = resources.displayMetrics.density
        val a = context.obtainStyledAttributes(attrs, R.styleable.BarChartView, defStyleAttr, 0)
        barPaint.color = a.getColor(R.styleable.BarChartView_chartBarColor, ContextCompat.getColor(context, R.color.buyer_primary))
        highlightPaint.color = a.getColor(R.styleable.BarChartView_chartHighlightColor, ContextCompat.getColor(context, R.color.accent_amber))
        labelPaint.color = a.getColor(R.styleable.BarChartView_chartLabelColor, ContextCompat.getColor(context, R.color.ink_600))
        cornerRadius = a.getDimension(R.styleable.BarChartView_chartCornerRadius, 6 * density)
        a.recycle()

        labelPaint.textSize = sp(11f)
        labelPaint.textAlign = Paint.Align.CENTER
        valuePaint.color = ContextCompat.getColor(context, R.color.ink_900)
        valuePaint.textSize = sp(10f)
        valuePaint.textAlign = Paint.Align.CENTER
        valuePaint.isFakeBoldText = true
        gridPaint.color = ContextCompat.getColor(context, R.color.border_sand)
        gridPaint.strokeWidth = density
    }

    /** Convierte sp a píxeles respetando el tamaño de fuente elegido por el usuario. */
    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    fun setData(data: List<SalesPoint>) {
        points = data
        contentDescription = data.joinToString { it.label + ": " + Format.moneyShort(it.value) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        val density = resources.displayMetrics.density
        val labelSpace = 22 * density
        val valueSpace = 18 * density
        val chartTop = paddingTop + valueSpace
        val chartBottom = height - paddingBottom - labelSpace
        val chartHeight = chartBottom - chartTop
        val slot = (width - paddingLeft - paddingRight).toFloat() / points.size
        val barWidth = slot * 0.56f
        val max = points.maxOf { it.value }.coerceAtLeast(1)

        // Líneas guía horizontales
        for (i in 0..3) {
            val y = chartBottom - chartHeight * i / 3f
            canvas.drawLine(paddingLeft.toFloat(), y, (width - paddingRight).toFloat(), y, gridPaint)
        }

        points.forEachIndexed { index, point ->
            val centerX = paddingLeft + slot * index + slot / 2
            val barHeight = chartHeight * point.value / max
            rect.set(centerX - barWidth / 2, chartBottom - barHeight, centerX + barWidth / 2, chartBottom)
            val paint = if (index == points.lastIndex) highlightPaint else barPaint
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            canvas.drawText(Format.moneyShort(point.value).removePrefix("$"), centerX, rect.top - 6 * density, valuePaint)
            canvas.drawText(point.label, centerX, chartBottom + 16 * density, labelPaint)
        }
    }
}
