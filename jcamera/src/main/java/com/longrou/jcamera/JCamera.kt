package com.longrou.jcamera

import android.app.Activity
import android.content.Intent
import com.longrou.jcamera.config.CameraConfig

/**
 * @Description:
 * @Author: LongRou
 * @CreateDate: 2019/5/23 13:05
 * @Version: 1.0
 **/
class JCamera {

    /**
     * 打开前置摄像头， 默认打开上次退出时候的摄像头
     */
    fun openPreCamera():JCamera{
        CameraConfig.last_camera_id = CameraConfig.FRONT_CAMERA_ID
        return this
    }
    /**
     * 设置最大录制时间
     */
    fun setMaxRecordTime(sec: Int):JCamera{
        if (sec < 1){return this}
        CameraConfig.MAX_RECORD_TIME = sec
        return this
    }
    /**
     * 是否允许录像
     */
    fun allowRecord(boolean: Boolean):JCamera{
        CameraConfig.IS_ALLOW_RECORD = boolean
        return this
    }
    /**
     * 是否允许拍照
     */
    fun allowPhoto(boolean: Boolean):JCamera{
        CameraConfig.IS_ALLOW_PHOTO = boolean
        return this
    }
    fun start(activity: Activity,requestCode: Int){
        val intent = Intent(activity, CameraActivity::class.java)
        activity.startActivityForResult(intent, requestCode)
    }
    companion object{
        private val TAG = "JCamera"
        const val CAPTURE_RESULT_IS_IMG = "CaptureResultIsImg"
        const val CAPTURE_RESULT = "CaptureResult"
        fun resultIsImg(data: Intent):Boolean{
           return data.getBooleanExtra(CAPTURE_RESULT_IS_IMG,false)
        }
        fun getResultPath(data: Intent):String{
            return data.getStringExtra(CAPTURE_RESULT)
        }
        val instance: JCamera by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            JCamera()
        }
    }
}