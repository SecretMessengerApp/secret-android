package com.jsy.common.utils
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.jsy.secret.sub.swipbackact.utils.LogUtils

class OrientationListener(val mContext: Context) : SensorEventListener {

    private val mSensorManager:SensorManager
    private val mRotationGeomagnetic:Sensor?
    private var mOnOrientationListener:OnOrientationListener? = null
    private var geomagneticR = FloatArray(9)
    private var geomagneticValues = FloatArray(3)

    interface OnOrientationListener {
        fun onOrientationChanged(azimuth: Float, pitch: Float, roll: Float)
    }

    init {
        mSensorManager = mContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mRotationGeomagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    }
    fun setOnOrientationListener(listener: OnOrientationListener?) {
        mOnOrientationListener = listener
    }

    fun registerListener() {
        if (mRotationGeomagnetic != null) {
            mSensorManager.registerListener(this, mRotationGeomagnetic, 100000, 100000)
        }
    }

    fun unregisterListener() {
        mSensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        try {
            if (event.sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(geomagneticR, event.values.clone())
                SensorManager.getOrientation(geomagneticR, geomagneticValues)
                val azimuth = Math.toDegrees(geomagneticValues[0].toDouble()).toFloat()
                val pitch = Math.toDegrees(geomagneticValues[1].toDouble()).toFloat()
                val roll = Math.toDegrees(geomagneticValues[2].toDouble()).toFloat()
                mOnOrientationListener?.onOrientationChanged(azimuth, pitch, roll)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtils.e("OrientationListener", "SensorData,error:${e.message}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

}
