package com.longrou.jcamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import com.daasuu.mp4compose.Rotation
import com.daasuu.mp4compose.composer.Mp4Composer
import com.longrou.jcamera.config.CameraConfig
import com.longrou.jcamera.databinding.ActivityCameraBinding
import com.longrou.jcamera.provider.CameraProvider
import com.longrou.jcamera.util.CameraUtil
import com.longrou.jcamera.util.ClickUtil
import com.longrou.jcamera.util.PermissionUtil
import com.longrou.jcamera.util.ScreenUtil
import com.longrou.jcamera.view.CircleProgressButton
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.lang.Long.signum
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * @Description: 拍摄主窗口
 * @Author: LongRou
 * @CreateDate: 2019/5/23 10:55
 * @Version: 1.0
 **/
class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding

    /**
     * 预览组件监听
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true
    }

    private val MAX_PREVIEW_WIDTH = lazy { (ScreenUtil.getScreenHeight(this)) }
    private val MAX_PREVIEW_HEIGHT = lazy { (ScreenUtil.getScreenWidth(this)) }
    private val MIN_RECORD_WIDHT = 960
    private val MIN_RECORD_HEIGHT = 1280

    private lateinit var cameraManager: CameraManager
    private lateinit var previewSession: CameraCaptureSession
    private lateinit var previewRequest: CaptureRequest
    private lateinit var previewBuilder: CaptureRequest.Builder

    private val cameraStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //得到相机 显示预览画面
            this@CameraActivity.cameraOpenCloseLock.release()
            this@CameraActivity.cameraDevice = camera
            state.set(STATE_PREVIEW)
            openCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            this@CameraActivity.cameraOpenCloseLock.release()
            camera.close()
            this@CameraActivity.cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            onDisconnected(camera)
            Toast.makeText(this@CameraActivity, getString(R.string.open_camera_error_tip), Toast.LENGTH_SHORT).show()
            this@CameraActivity.finish()
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
        }
    }
    private lateinit var cameraList: Array<String>
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    /**
     * 传感器定位
     */
    private var sensorOrientation = 0
    private var recordOrientation = 0
    private lateinit var orientationEventListener: OrientationEventListener
    /**
     * 用户操作
     */
    private var isPressRecord = false
    //当前视图显示阶段
    var state: ObservableField<Int> = ObservableField(STATE_PREVIEW)
    var captureImage: Image? = null
    private lateinit var recordSize: Size
    private lateinit var previewSize: Size
    /**
     * Record
     */
    private var recordPath: String = ""
    private var comRecordPath: String = ""
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var mediaPlayer: MediaPlayer
    /**
     * Preview
     */
    private lateinit var previewSurface: Surface
    /**
     * Photo
     */
    private lateinit var previewImageReader: ImageReader
    private lateinit var previewImageReaderSurface: Surface


    //操作回调
    private var launchRunnable = Runnable {
        //如果还处于按下状态 表示要录像
        if (isPressRecord && CameraConfig.IS_ALLOW_RECORD) {
            //拍摄开始启动
            binding.mBtnRecord.start()
        }
    }
    private var startRecordRunnable = Runnable {
        if (isPressRecord && CameraConfig.IS_ALLOW_RECORD) {
            startRecord()
        }
    }

    /**
     * 操作信号量
     */
    private val cameraOpenCloseLock = Semaphore(1)
    private val sessionOpenCloseLock = Semaphore(1)

    /**
     * CAMERA 初始化
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 28) {
            //刘海屏 整死我了！！！！！！！！！！！！！
            val lp = window.attributes
            lp?.let {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
        }
        binding = DataBindingUtil.setContentView<ActivityCameraBinding>(this, R.layout.activity_camera)
        binding.activity = this
        init()
    }

    override fun onBackPressed() {
        when (state.get()){
            STATE_RECORDING ->{
                //录像状态 禁止退出
            }
            STATE_RECORD_TAKEN ->{
                //播放状态 需要先删除文件后退出
                File(comRecordPath).delete()
                finish()
            }
            else->{
                //其余状态 删除临时录像文件后退出
                File(recordPath).delete()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        when (state.get()) {
            STATE_RECORD_TAKEN -> {
                //捕获录像状态 继续播放
                mediaPlayer.start()
            }
            STATE_PICTURE_TAKEN -> {
                //捕获照片状态 不做处理
            }
            else -> {
                //预览视图恢复默认
                if (binding.mTextureView.isAvailable) {
                    openCamera()
                } else {
                    binding.mTextureView.surfaceTextureListener = this@CameraActivity.surfaceTextureListener
                }
            }
        }
    }

    override fun onPause() {
        when (state.get()) {
            STATE_RECORDING -> {
                //录像状态 停止录像
                binding.mBtnRecord.stop()
                stopRecord()
            }
            STATE_RECORD_TAKEN -> {
                //捕获录像状态 暂停播放
                mediaPlayer.pause()
            }
            STATE_PREVIEW, STATE_PICTURE_TAKEN -> {
                closeCamera()
            }
        }
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationEventListener.disable()
        if (::previewSurface.isInitialized) {
            previewSurface.release()
        }
        if (::mediaRecorder.isInitialized) {
            mediaRecorder.release()
        }
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").also { it.start() }
            backgroundHandler = Handler(backgroundThread?.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CameraConfig.CAMERA_PERMISSION_CODE) {
            if (grantResults.size >= 1) {
                val cameraResult = grantResults[0]//相机权限
                val sdResult = grantResults[1]//sd卡权限
                val audioResult = grantResults[2]//录音权限
                val cameraGranted = cameraResult == PackageManager.PERMISSION_GRANTED//拍照权限
                val sdGranted = sdResult == PackageManager.PERMISSION_GRANTED//sd卡权限
                val audioGranted = audioResult == PackageManager.PERMISSION_GRANTED//录音权限
                if (cameraGranted && sdGranted && audioGranted) {
                    //具有所需权限 继续打开摄像头
                    //openCamera()
                } else {
                    //没有权限
                    Toast.makeText(this, getString(R.string.no_permission_tip), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun init() {
        binding.mTvRecordTip.text = "${if (CameraConfig.IS_ALLOW_PHOTO)getString(R.string.photo_tip)else ""}${if (CameraConfig.IS_ALLOW_PHOTO && CameraConfig.IS_ALLOW_RECORD)"," else ""}${if (CameraConfig.IS_ALLOW_RECORD)getString(R.string.record_tip)else ""}"
        initCamera()
        initTouchListener()
    }

    /**
     * 初始化摄像头
     */
    private fun initCamera() {
        cameraManager = applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraList = cameraManager.cameraIdList
        if (!CameraConfig.init) {
            // 设置正反面摄像头ID
            cameraList.forEach { cId ->
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(cId)
                if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT) {
                    CameraConfig.FRONT_CAMERA_ID = cId
                    CameraConfig.FRONT_CAMERA_CHARACTERISTIC = cameraCharacteristics
                } else if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK) {
                    Log.e(TAG,"cId :$cId LENS_FACING_BACK $cameraCharacteristics")
                    val streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val supportedSizes = streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)
                    if (supportedSizes != null) {
                        for (size in supportedSizes) {
                            val aspectRatio = (size.width.toFloat() / size.height.toFloat())
                            Log.e("CameraUtil","aspectRatio $aspectRatio - width ${size.width.toFloat()} - height ${ size.height.toFloat()}")
                        }
                    }
                    if (CameraConfig.BACK_CAMERA_ID.isBlank()){
                        // 多后摄像头 取第一个
                        CameraConfig.BACK_CAMERA_ID = cId
                        CameraConfig.BACK_CAMERA_CHARACTERISTIC = cameraCharacteristics
                    }
                }
            }
            CameraConfig.init = true
        }

        //初始化传感器定位
        sensorOrientation = CameraConfig.getCurrentCameraCameraCharacteristics().get(CameraCharacteristics.SENSOR_ORIENTATION)
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (45 <= orientation && orientation < 135) {
                    sensorOrientation = 90
                } else if (135 <= orientation && orientation < 225) {
                    sensorOrientation = 180
                } else if (225 <= orientation && orientation < 315) {
                    sensorOrientation = 270
                } else {
                    sensorOrientation = 0
                }
            }
        }
        orientationEventListener.enable()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        //权限界定
        if (!PermissionUtil.checkPermission(this)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO), CameraConfig.CAMERA_PERMISSION_CODE)
        } else {
            try {
                // 3秒内打开摄像头
                if (!cameraOpenCloseLock.tryAcquire(3000, TimeUnit.MILLISECONDS)) {
                    throw RuntimeException("Time out waiting to lock camera opening.")
                }
                cameraManager.openCamera(CameraConfig.last_camera_id, cameraStateCallback, backgroundHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, e.toString())
            } catch (e: InterruptedException) {
                throw RuntimeException("Interrupted while trying to lock camera opening.", e)
            }
        }
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            if (::previewSession.isInitialized) {
                previewSession.close()
            }
            cameraDevice?.close()
            cameraDevice = null
            if (::previewImageReader.isInitialized) {
                previewImageReader.close()
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun initPreview() {
        //设置预览尺寸
        if (!::previewSize.isInitialized){
            var size = CameraUtil.getMaxOptimalSize(CameraConfig.getCurrentCameraCameraCharacteristics(), SurfaceTexture::class.java,
                    binding.mTextureView.height, binding.mTextureView.width)
            if (size == null) {
                size = Size(MAX_PREVIEW_WIDTH.value, MAX_PREVIEW_HEIGHT.value)
            }
            previewSize = size
        }
        //设置录像尺寸
        if (!::recordSize.isInitialized){
            val mRecordSize = CameraUtil.getMinOptimalSize(CameraConfig.getCurrentCameraCameraCharacteristics(), SurfaceTexture::class.java,
                    binding.mTextureView.height, binding.mTextureView.width)
            if (mRecordSize == null) {
                recordSize = Size(MIN_RECORD_WIDHT, MIN_RECORD_HEIGHT)
            } else {
                recordSize = mRecordSize
            }
        }
        //创建ImageReader接收拍照数据
        val imageFormat = ImageFormat.JPEG
        val streamConfigurationMap = CameraConfig.getCurrentCameraCameraCharacteristics()[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
        if (streamConfigurationMap?.isOutputSupportedFor(imageFormat) == true) {
            // JPEG is supported
            previewImageReader = ImageReader.newInstance(previewSize.width, previewSize.height, imageFormat, 1)
            previewImageReader.setOnImageAvailableListener(OnJpegImageAvailableListener(), backgroundHandler)
            previewImageReaderSurface = previewImageReader.surface
        }
        //创建MediaRecorder用于录像
        if (!::mediaRecorder.isInitialized) {
            mediaRecorder = MediaRecorder()
        }

        //创建MediaPlayer用于播放
        if (!::mediaPlayer.isInitialized) {
            mediaPlayer = MediaPlayer()
        }
    }

    fun changeCamera() {
        if (ClickUtil.isFastClick()) return
        closeCamera()
        when (CameraConfig.last_camera_id) {
            CameraConfig.FRONT_CAMERA_ID -> {
                CameraConfig.last_camera_id = CameraConfig.BACK_CAMERA_ID
            }
            CameraConfig.BACK_CAMERA_ID -> {
                CameraConfig.last_camera_id = CameraConfig.FRONT_CAMERA_ID
            }
        }
        openCamera()
    }

    /**
     * 开始输出预览信息
     */
    private fun openCameraPreviewSession() {
        initPreview()
        previewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        val outputList = mutableListOf<Surface>()
        //重置录像
        setUpMediaRecorder()
        outputList.add(mediaRecorder.surface)
        previewBuilder.addTarget(mediaRecorder.surface)
        // 照片
        if (::previewImageReaderSurface.isInitialized) {
            outputList.add(previewImageReaderSurface)
        }
        //预览视图
        val surfaceTexture = binding.mTextureView.surfaceTexture
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        previewSurface = Surface(surfaceTexture)
        outputList.add(previewSurface)
        previewBuilder.addTarget(previewSurface)

        previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        cameraDevice?.createCaptureSession(outputList, object : CameraCaptureSession.StateCallback() {

            override fun onConfigureFailed(session: CameraCaptureSession) {

            }

            override fun onConfigured(session: CameraCaptureSession) {
                //相机未打开
                if (cameraDevice == null) {
                    return
                }
                previewRequest = previewBuilder.build()
                previewSession = session
                previewSession.setRepeatingRequest(previewRequest, null, backgroundHandler)
            }
        }, backgroundHandler)
    }
    /**
     * 解除锁定
     */
    fun unlockFocus() {
        //防抖
        if (ClickUtil.isFastClick()) return
        try {
            // 视频需要删除暂存文件
            if (state.get() == STATE_RECORD_TAKEN) {
                //停止播放
                mediaPlayer.stop()
                mediaPlayer.reset()
                File(comRecordPath).delete()
                //预览视图恢复默认
                val matrix = Matrix()
                binding.mTextureView.getTransform(matrix)
                matrix.setScale(1f,  1f)
                matrix.postTranslate(0f, 0f)
                binding.mTextureView.setTransform(matrix)
                openCamera()
            } else {
                // Reset the auto-focus trigger
                if(cameraDevice == null){
                    openCamera()
                }else {
                    previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                    state.set(STATE_PREVIEW)
                    previewSession?.setRepeatingRequest(previewRequest, null,
                            backgroundHandler)
                }
            }

        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    /**
     * 捕获当前结果
     */
    fun captureResult() {
        //防抖
        if (ClickUtil.isFastClick()) return
        when (state.get()) {
            STATE_PICTURE_TAKEN -> {
                //捕获图片 先删除录像临时文件
                File(recordPath).delete()
                captureImage?.let { mImage ->
                    val buffer = mImage.planes[0].buffer
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    val timeStamp = getDate()
                    val filePath = "${CameraConfig.getSaveDir(this@CameraActivity)}/img_$timeStamp.jpg"
                    var isBackCamera = CameraConfig.last_camera_id == CameraConfig.BACK_CAMERA_ID

                    var fos: FileOutputStream? = null
                    var bitmap: Bitmap? = null
                    var matBitmap: Bitmap? = null
                    try {
                        fos = FileOutputStream(filePath)
                        fos!!.write(data, 0, data.size)
                        if (!isBackCamera) {
                            //前置摄像头 图片需要镜像
                            val matrix = Matrix()
                            matrix.setScale(-1f, 1f)
                            matrix.postRotate((sensorOrientation.toFloat() + 90) % 360 )
                            fos.close()
                            bitmap = BitmapFactory.decodeFile(filePath)
                            matBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            fos = FileOutputStream(filePath)
                            matBitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                        }
                        val intent = Intent()
                        intent.putExtra(JCamera.CAPTURE_RESULT_IS_IMG, true)
                        intent.putExtra(JCamera.CAPTURE_RESULT, filePath)
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        mImage.close()
                        try {
                            bitmap?.recycle()
                            matBitmap?.recycle()
                            fos?.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            STATE_RECORD_TAKEN -> {
                //捕获视频
                val intent = Intent()
                intent.putExtra(JCamera.CAPTURE_RESULT_IS_IMG, false)
                intent.putExtra(JCamera.CAPTURE_RESULT, if (!comRecordPath.isNullOrBlank())comRecordPath else recordPath)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }

    }

    /**
     * ************************************************************* 界面控制
     */
    private fun showBtnLayout() {
        binding.mBtnCancel.startAnimation(leftAction)
        binding.mBtnOK.startAnimation(rightAction)
    }

    private fun initTouchListener() {
        //初始化按钮录像完毕回调
        binding.mBtnRecord.setOnFinishCallBack(object : CircleProgressButton.OnFinishCallback {
            override fun progressStart() {
                backgroundHandler?.postDelayed(startRecordRunnable,0L)
            }

            override fun progressFinish() {
                //录像到最大时间 直接结束录像
                isPressRecord = false
                unPressRecord()
            }
        })
        //初始化触摸事件
        binding.mBtnRecord.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPressRecord = true
                    //
                    backgroundHandler?.postDelayed(launchRunnable, 500)
                }
                MotionEvent.ACTION_MOVE -> {
//                    Log.e(TAG,"ACTION_MOVE Y: ${event.y}")
//                    if (event.y < 0 && state.get() == STATE_RECORDING){
//                        //录像中滑动变焦
//                    }
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    //录像到最大时间而提前结束也会触发， 防止重复调用
                    if (isPressRecord) {
                        isPressRecord = false
                        unPressRecord()
                    }
                }
            }
            true
        }
    }

    private fun unPressRecord() {
        backgroundHandler?.removeCallbacks(launchRunnable)
        when (state.get()) {
            STATE_PREVIEW -> {
                if (CameraConfig.IS_ALLOW_PHOTO) {
                    //手指松开还未开始录像 进行拍照
                    takePhoto()
                }
            }
            STATE_RECORDING -> {
                //正在录像 停止录像
                binding.mBtnRecord.stop()
                stopRecord()
            }
        }
    }

    /**
     * ***********************************************************take photo
     */
    private fun takePhoto() {
        if (cameraDevice == null || !binding.mTextureView.isAvailable) {
            return
        }
        try {
            val captureBuilder = cameraDevice?.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {
                if (::previewImageReaderSurface.isInitialized) {
                    addTarget(previewImageReaderSurface)
                }
                set(CaptureRequest.JPEG_ORIENTATION,
                        CameraUtil.getJpegOrientation(CameraConfig.getCurrentCameraCameraCharacteristics(), sensorOrientation))
                set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                //锁定焦点
                set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_START)
            }

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult) {
                    //closeCamera()
                    state.set(STATE_PICTURE_TAKEN)
                    showBtnLayout()
                }
            }
            previewSession.apply {
                stopRepeating()
                //abortCaptures()
                //拍照
                capture(captureBuilder?.build(), captureCallback, null)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * ***********************************************************record
     */
    private fun startRecord() {
        sessionOpenCloseLock.acquire()
        if (state.get() != STATE_RECORDING) {
            state.set(STATE_RECORDING)
            try {
                //记录拍摄时 手机方向
                recordOrientation = sensorOrientation
                //开始录像
                mediaRecorder.start()
                sessionOpenCloseLock.release()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CameraActivity, getString(R.string.open_camera_error_tip), Toast.LENGTH_SHORT).show()
                this@CameraActivity.finish()
            }
        }
    }
    
    private fun stopRecord() {
        sessionOpenCloseLock.acquire()
        if (state.get() == STATE_RECORDING) {
            state.set(STATE_RECORD_TAKEN)
            showBtnLayout()
            try {
                mediaRecorder.stop()
                closeCamera()
                //.....
                mediaPlayer.reset()
                var isBackCamera = CameraConfig.last_camera_id == CameraConfig.BACK_CAMERA_ID
                //解决镜像问题
                Mp4Composer(recordPath,comRecordPath).rotation(correctRecord()).flipHorizontal(!isBackCamera).listener(object : Mp4Composer.Listener{
                    override fun onFailed(exception: Exception?) {
                    }
                    override fun onProgress(progress: Double) {
                    }
                    override fun onCanceled() {
                    }
                    override fun onCompleted() {
                        val uri: Uri
                        if (Build.VERSION.SDK_INT >= 24) {
                            uri = FileProvider.getUriForFile(this@CameraActivity, CameraProvider.getFileProviderName(this@CameraActivity), File(comRecordPath))
                        } else {
                            uri = Uri.fromFile(File(comRecordPath))
                        }
                        mediaPlayer.setDataSource(this@CameraActivity, uri)
                        //AudioAttributes是一个封装音频各种属性的类
                        val attrBuilder = AudioAttributes.Builder()
                        //************************************* 横向拍摄需要修改preview显示方向
                        if (recordOrientation == 90 || recordOrientation == 270){
                            setHorizontalPreview()
                        }
                        //*************************************
                        //设置音频流的合适属性
                        attrBuilder.setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        mediaPlayer.setAudioAttributes(attrBuilder.build())
                        mediaPlayer.setSurface(Surface(binding.mTextureView.surfaceTexture))
                        mediaPlayer.setOnPreparedListener {
                            mediaPlayer.isLooping = true
                            mediaPlayer.start()
                            //移除原视频
                            File(recordPath).delete()
                        }
                        mediaPlayer.prepare()
                    }
                }).start()
            }catch (e: Exception){
                //e.printStackTrace()
                Toast.makeText(this@CameraActivity, getString(R.string.record_time_short), Toast.LENGTH_SHORT).show()
                File(recordPath).delete()
                previewSession.apply {
                    stopRepeating()
                    abortCaptures()
                }
                unlockFocus()
            }finally {
                sessionOpenCloseLock.release()
            }
        }else {
            //已经被操作了 直接释放信号
            sessionOpenCloseLock.release()
        }
    }

    /**
     * 配置MediaRecorder
     */
    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        mediaRecorder.reset()
        val timeStamp = getDate()
        //删除上一个临时视频
        if (!recordPath.isNullOrBlank()){File(recordPath).delete()}
        recordPath = "${CameraConfig.getSaveDir(this@CameraActivity)}/mov_$timeStamp.mp4"
        comRecordPath = "${CameraConfig.getSaveDir(this@CameraActivity)}/mov_${timeStamp}comp.mp4"
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(recordPath)
            setVideoEncodingBitRate(1024 * 1024)
            setVideoFrameRate(30)
            setMaxDuration(CameraConfig.MAX_RECORD_TIME * 1000)
            setVideoSize(recordSize.width, recordSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }
    }

    private fun setHorizontalPreview(){
        var aspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (previewSize.height > (previewSize.height * aspectRatio).toInt()) {
            // limited by narrow width; restrict height
            newWidth = previewSize.width
            newHeight = (previewSize.width * aspectRatio).toInt()
        } else {
            // limited by short height; restrict width
            newWidth = (previewSize.height / aspectRatio).toInt()
            newHeight = previewSize.height
        }
        val xoff = (previewSize.width - newWidth) / 2
        val yoff = (previewSize.height - newHeight) / 2
        val matrix = Matrix()
        binding.mTextureView.getTransform(matrix)
        matrix.setScale(1f,  newWidth.toFloat() / previewSize.width.toFloat())
        matrix.postTranslate(0f, xoff.toFloat())
        binding.mTextureView.setTransform(matrix)
    }
    private fun correctRecord(): Rotation {
        val rotation:Rotation
        when (CameraUtil.getJpegOrientation(CameraConfig.getCurrentCameraCameraCharacteristics(), recordOrientation)){
            0->rotation = Rotation.NORMAL
            90->rotation = Rotation.ROTATION_90
            180->rotation = Rotation.ROTATION_180
            270->rotation = Rotation.ROTATION_270
            else->rotation = Rotation.NORMAL
        }
        return rotation
    }

    private inner class OnJpegImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            captureImage?.let {
                it.close()
                captureImage = null
            }
            captureImage = reader.acquireLatestImage()
        }
    }

    companion object {
        /**
         * 捕获按钮动画
         */
        private val leftAction: TranslateAnimation = TranslateAnimation(Animation.RELATIVE_TO_SELF, 1.5f, Animation.RELATIVE_TO_SELF,
                0f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f)
        private val rightAction: TranslateAnimation = TranslateAnimation(Animation.RELATIVE_TO_SELF, -1.5f, Animation.RELATIVE_TO_SELF,
                0f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f)

        init {
            leftAction.duration = 200
            rightAction.duration = 200
        }

        private const val TAG = "CameraActivity"
        /**
         * Camera state: 预览相机
         */
        const val STATE_PREVIEW = 0x00000000

        /**
         * Camera state: 拍照捕获图片
         */
        const val STATE_PICTURE_TAKEN = 0x00000001

        /**
         * Camera state: 正在录像
         */
        const val STATE_RECORDING = 0x00000002
        /**
         * Camera state: 捕获录像
         */
        const val STATE_RECORD_TAKEN = 0x00000003


        private fun getDate(): String {
            var ca = Calendar.getInstance()
            var year = ca.get(Calendar.YEAR)           // 获取年份
            var month = ca.get(Calendar.MONTH)         // 获取月份
            var day = ca.get(Calendar.DATE)            // 获取日
            var minute = ca.get(Calendar.MINUTE)       // 分
            var hour = ca.get(Calendar.HOUR)           // 小时
            var second = ca.get(Calendar.SECOND)       // 秒
            return "" + year + (month + 1) + day + hour + minute + second
        }
    }

    internal class CompareSizesByArea : Comparator<Size> {
        // We cast here to ensure the multiplications won't overflow
        override fun compare(lhs: Size, rhs: Size) =
                signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)

    }
}