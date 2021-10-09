package com.jsy.common.view

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.jsy.common.utils.OrientationListener


class ConversationBackgroundLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RelativeLayout(context, attrs), OrientationListener.OnOrientationListener {

    private var listener: OrientationListener? = null
    private var sensor: Boolean
    private var isRegisterSensor: Boolean = false
    private var lastAzimuth:Float = 0.0f
    private var lastPitch:Float = 0.0f
    private var lastRoll:Float = 0.0f
    private var lastTranslationX = 0.0f
    private var lastTranslationY = 0.0f

    companion object{
        const val MIN_ROLL=5
        const val MAX_TRANSLATION = 20f
    }

    init {
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O) {
            sensor = true
            listener = OrientationListener(context).apply {
                setOnOrientationListener(this@ConversationBackgroundLayout)
            }
        } else {
            sensor = false
        }
    }

    fun isSensored(): Boolean {
        return this.sensor
    }

    fun setSensored(isSensor: Boolean) {
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O) {
            this.sensor = isSensor
            if (!this.sensor) {
                onPause()
            }
        } else {
            this.sensor = false
        }
    }

    override fun onOrientationChanged(azimuth: Float, pitch: Float, roll: Float) {
        if (lastPitch == pitch && lastRoll == roll) {
            return
        }
        val transX = getTrans(roll)
        val transY = getTrans(-pitch)
        if (lastTranslationX != transX) {
            if (translationX != transX) {
                translationX = transX
                lastTranslationX = transX
            }
        }
        if (lastTranslationY != transY) {
            if (translationY != transY) {
                translationY = transY
                lastTranslationY = transY
            }
        }
        lastAzimuth = azimuth
        lastPitch = pitch
        lastRoll = roll
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if(sensor){
            val width= MeasureSpec.getSize(widthMeasureSpec)
            val height= MeasureSpec.getSize(heightMeasureSpec)
            val newWidth=(width+ MAX_TRANSLATION*2).toInt()
            val newHeight=(height+ MAX_TRANSLATION*2).toInt()
            setMeasuredDimension(newWidth, newHeight)
        }

    }

    private fun getTrans(data: Float): Float {
        var trans = 0f
        if (Math.abs(data) >= MIN_ROLL) {
            trans = data
            if (trans >= MAX_TRANSLATION) {
                trans = MAX_TRANSLATION
            } else if (trans <= -MAX_TRANSLATION) {
                trans = -MAX_TRANSLATION
            }
        }
        return trans
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onPause()
    }

    fun onResume() {
        if (sensor && !isRegisterSensor && isAttachedToWindow) {
            listener?.registerListener()
            isRegisterSensor = true
        }
    }

    fun onPause() {
        if (isRegisterSensor) {
            listener?.unregisterListener()
            isRegisterSensor = false
        }
    }

    fun onHiddenChanged(hidden: Boolean){
        if (hidden) {
            onPause()
        } else {
            onResume()
        }
    }

    fun setUserVisibleHint(isVisibleToUser: Boolean){
        if(isVisibleToUser){
            onResume()
        }else{
            onPause()
        }
    }
}
