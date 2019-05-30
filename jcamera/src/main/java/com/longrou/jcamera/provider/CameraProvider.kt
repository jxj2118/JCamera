package com.longrou.jcamera.provider

import android.content.Context
import androidx.core.content.FileProvider

/**
 * @Description:
 * @Author: LongRou
 * @CreateDate: 2019/5/23 11:24
 * @Version: 1.0
 **/
class CameraProvider : FileProvider() {
    companion object{
        fun getFileProviderName(context: Context): String {
            return context.packageName + ".provider"
        }
    }
}