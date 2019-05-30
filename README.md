# JCamera
[![Platform](https://img.shields.io/badge/platform-android-green.svg)](http://developer.android.com/index.html)
<img src="https://img.shields.io/badge/license-MIT-green.svg?style=flat">
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
This is Android CameraActivity,Imitation WeChat Camera

Android 仿微信视频拍摄 支持触摸拍摄 长按拍摄，采用camera2，需要API>21 后续考虑支持API>18

解决前置摄像头镜像问题，视频压缩采用了MasayukiSuda 的 https://github.com/MasayukiSuda/Mp4Composer-android  ，会在原视频输出后占用600-700ms压缩及旋转视频

支持一步调用

<table>
    <td><img src="art/1.gif"><br>视频拍摄<br>video</td>
    <td><img src="art/2.gif"><br>图片拍摄<br>photo</td>
</table>

## Gradle
