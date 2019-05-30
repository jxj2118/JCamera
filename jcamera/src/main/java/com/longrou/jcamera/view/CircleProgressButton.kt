package com.longrou.jcamera.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.view.View
import java.util.*


/**
 * @Description: 写得我脑壳痛
 * @Author: LongRou
 * @CreateDate: 2019/5/27 14:40
 * @Version: 1.0
 **/
class CircleProgressButton : View{
    var processSec = 5
    /**
     * 进度条步进
     */
    private val processStep = lazy { maxPro /(processSec * 1000 / REFLASH_TIEM) }
    private lateinit var callback: OnFinishCallback


    private lateinit var bgPaint: Paint
    private lateinit var conPaint: Paint
    private lateinit var proPaint: Paint
    private lateinit var rectF: RectF
    private val REFLASH_TIEM = 50L
    private var maxPro = 10000f
    private var curPro = 0f
    private var arc: Float = 0f
    private var cWidth = 0f
    private var cHeight = 0f
    private var showBigButton = false
    private var bigFactor = 0.8f
    private var starting = false

    private var cHandler : Handler
    private var tr : Timer? = null

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr){
        cHandler = object : Handler(){
            override fun handleMessage(msg: Message?) {
                super.handleMessage(msg)
                postInvalidate()
            }
        }
        initPaint()
    }
    private fun initPaint(){

        bgPaint = Paint()
        bgPaint.isAntiAlias = true
        bgPaint.color = Color.parseColor("#DDDDDD")
        bgPaint.strokeWidth = 30f
        bgPaint.style = Paint.Style.FILL

        conPaint = Paint()
        conPaint.isAntiAlias = true
        conPaint.color = Color.parseColor("#FFFFFF")
        conPaint.strokeWidth = 30f
        conPaint.style = Paint.Style.FILL

        proPaint = Paint()
        proPaint.isAntiAlias = true
        proPaint.color = Color.parseColor("#3ec88e")
        proPaint.style = Paint.Style.STROKE

        rectF = RectF()
    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        cWidth = width.toFloat()
        cHeight = height.toFloat()
        if (cWidth != cHeight) {
            val min = Math.min(cWidth, cHeight)
            cWidth = min
            cHeight = min
        }
        arc = cWidth / 14f
        proPaint.strokeWidth = arc
        if (starting){
            if (!showBigButton){
                //缓慢放大
                bigFactor += 0.025f
                if (bigFactor >= 0.99f){bigFactor = 1f}
                canvas?.drawCircle((cWidth / 2), (cHeight / 2), (cWidth / 2) * bigFactor, bgPaint)
                canvas?.drawCircle((cWidth / 2), (cHeight / 2), ((cWidth / 2) * (1.40 - bigFactor)).toFloat(), conPaint)
                if (bigFactor >= 0.99f){
                    //放大完毕
                    showBigButton = true
                    postInvalidate()
                    //通知调用者 开始
                    if (::callback.isInitialized){
                        callback.progressStart()
                    }
                }else{
                    cHandler.sendEmptyMessageDelayed(0, 10)
                }
            }else {
                //开始绘制进度
                canvas?.drawCircle((cWidth / 2), (cHeight / 2), (cWidth / 2) * bigFactor, bgPaint)
                canvas?.drawCircle((cWidth / 2), (cHeight / 2), ((cWidth / 2) * (1.40 - bigFactor)).toFloat(), conPaint)
                if (curPro < maxPro) {
                    rectF.left = 0f + arc / 2
                    rectF.top = 0f + arc / 2
                    rectF.right = cWidth - arc / 2
                    rectF.bottom = cWidth - arc / 2
                    canvas?.drawArc(rectF, -90f, curPro / maxPro * 360, false, proPaint)
                    curPro += processStep.value
                }else {
                    //通知调用者 结束
                    if (::callback.isInitialized){
                        callback.progressFinish()
                    }
                    stop()
                    return
                }
                if (tr == null){
                    //定时绘制
                    tr = Timer()
                    tr?.schedule(object : TimerTask(){
                        override fun run() {
                            postInvalidate()
                        }
                    },0L,REFLASH_TIEM)
                }
            }

            //postInvalidate()
        }else{
            if (showBigButton){
                //缓慢缩小
                bigFactor -= 0.025f
                if (bigFactor <= 0.81f){bigFactor = 0.8f}
                canvas?.drawCircle((cWidth / 2), (cHeight / 2), (cWidth / 2) * bigFactor, bgPaint)
                canvas?.drawCircle((cWidth / 2), (cHeight / 2), ((cWidth / 2) * 0.75f * (1.55f - bigFactor)), conPaint)
                if (bigFactor <= 0.81f){
                    //还原完毕
                    showBigButton = false
                }else{
                    cHandler.sendEmptyMessageDelayed(0, 10)
                }
            }else{
                canvas?.drawCircle((cWidth / 2), (cHeight / 2), (cWidth / 2) * bigFactor, bgPaint)
                canvas?.drawCircle((cWidth / 2), (cHeight / 2), ((cWidth / 2) * 0.75f * bigFactor), conPaint)
                showBigButton = false
            }
        }
    }
    fun start(){
        starting = true
        postInvalidate()
    }
    fun stop(){
        starting = false
        curPro = 0f
        tr?.cancel()
        tr = null
        cHandler.removeMessages(0)
        postInvalidate()
    }
    fun setOnFinishCallBack(callback: OnFinishCallback){
        this.callback = callback
    }
    interface OnFinishCallback {
        fun progressStart()
        fun progressFinish()
    }
}