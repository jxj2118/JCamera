package com.longrou.jcamera.util

/**
 * @Description:
 * @Author: LongRou
 * @CreateDate: 2019/4/22 14:23
 * @Version: 1.0
 **/
class ClickUtil {
    companion object {
        const val MIN_DELAY_TIME = 500L
        var lastClickTime:Long = 0
        fun isFastClick():Boolean{
            var flag = true
            var currentClickTime = System.currentTimeMillis()
            if ((currentClickTime - lastClickTime) >= MIN_DELAY_TIME) {
                flag = false
            }
            lastClickTime = currentClickTime
            return flag
        }
    }
}