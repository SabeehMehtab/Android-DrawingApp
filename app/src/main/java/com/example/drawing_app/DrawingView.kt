package com.example.drawing_app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context,attrs: AttributeSet ) : View(context, attrs) {
    private var drawPath: CustomPath? = null
    private var canvasBitmap: Bitmap? = null
    private var drawPaint: Paint? = null
    private var canvasPaint: Paint? = null
    private var brushSize: Float = 1.toFloat()
    private var colorDraw = Color.BLACK
    private var canvas: Canvas? = null
    private val paths = ArrayList<CustomPath> ()
    private val undoPaths = ArrayList<CustomPath> ()

    init {
        setup()
    }

    fun onClickUndo(){
        if(paths.size > 0) {
            undoPaths.add(paths.removeAt(paths.size-1))
            invalidate()
        }
    }

    fun onClickRedo(){
        if(undoPaths.size > 0) {
            paths.add(undoPaths.removeAt(undoPaths.size -1))
            invalidate()
        }
    }

    fun clearAll(){
        if (paths.size > 0) {
            paths.removeAll(paths.toSet())
            invalidate()
        }
    }

    private fun setup() {

        drawPath = CustomPath(colorDraw,brushSize)
        drawPaint = Paint()
        drawPaint!!.color = colorDraw
        drawPaint!!.style = Paint.Style.STROKE
        drawPaint!!.strokeJoin = Paint.Join.ROUND
        drawPaint!!.strokeCap = Paint.Cap.ROUND
        canvasPaint = Paint(Paint.DITHER_FLAG)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(canvasBitmap!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap!!, 0F,0F, canvasPaint)
        for(path in paths) {
            drawPaint!!.strokeWidth = path.brushThickness
            drawPaint!!.color = path.color
            canvas.drawPath(path,drawPaint!!)
        }

        if (!drawPath!!.isEmpty) {
            drawPaint!!.strokeWidth = drawPath!!.brushThickness
            drawPaint!!.color = drawPath!!.color
            canvas.drawPath(drawPath!!,drawPaint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val touchX = event?.x
        val touchY = event?.y

        when(event?.action){
            MotionEvent.ACTION_DOWN -> {
                drawPath!!.color = colorDraw
                drawPath!!.brushThickness = brushSize
                drawPath!!.moveTo(touchX!!,touchY!!)

            }
            MotionEvent.ACTION_MOVE -> {
                drawPath!!.lineTo(touchX!!,touchY!!)
            }
            MotionEvent.ACTION_UP -> {
                paths.add(drawPath!!)
                drawPath = CustomPath(colorDraw,brushSize)
            }
            else -> return false
        }

        invalidate()
        return true
    }

    fun setBrushSize(newSize: Float) {
        brushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
            newSize,resources.displayMetrics)
        drawPaint!!.strokeWidth = brushSize
    }

    fun setBrushColor(rgb : Int) {
        colorDraw = rgb
        drawPaint!!.color = colorDraw
    }
    internal inner class CustomPath(var color : Int, var brushThickness: Float) : Path(){

    }
}