package com.longrou.jcamera.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.longrou.jcamera.JCamera
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    val PHOTO_OR_VIDEO_FOR_CAMERA = 0x3701
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        text.text = "请按下拍照按钮\nPlease pressed camera button"
        fab.setOnClickListener {
            //如不设置 会默认打开上一次配置的结果
            JCamera.instance
                //.openPreCamera()// 是否打开为前置摄像头
                .allowPhoto(true)// 是否允许拍照 默认允许
                .allowRecord(true)// 是否允许录像 默认允许
                .setMaxRecordTime(3)//最长录像时间 秒
                .start(this,PHOTO_OR_VIDEO_FOR_CAMERA)//PHOTO_OR_VIDEO_FOR_CAMERA 请求码 回调时可用
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK &&  requestCode == PHOTO_OR_VIDEO_FOR_CAMERA){
            data?.let {
                if (JCamera.resultIsImg(data)){
                    text.text = "Image Path：\n${JCamera.getResultPath(data)}"
                }else{
                    text.text = "Video Path：\n${JCamera.getResultPath(data)}"
                }
            }
        }
    }
}
