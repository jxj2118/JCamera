package com.longrou.jcamera.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * @Description: 权限工具
 * @Author: LongRou
 * @CreateDate: 2019/5/23 12:15
 * @Version: 1.0
 **/
class PermissionUtil {
    companion object{
        /**
         * 检查本库所需权限
         */
        fun checkPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
    }
}