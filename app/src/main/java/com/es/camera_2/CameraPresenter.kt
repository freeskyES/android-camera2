package com.es.camera_2

import android.Manifest
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.RequiresApi
import android.support.annotation.RequiresPermission
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.es.camera_2.CameraFragment.Companion.MAX_PREVIEW_HEIGHT
import com.es.camera_2.CameraFragment.Companion.MAX_PREVIEW_WIDTH
import com.es.camera_2.CameraFragment.Companion.STATE_WAITING_LOCK
import com.es.camera_2.manager.ImageSaver
import com.es.camera_2.manager.exceptions.CamStateException
import com.es.camera_2.manager.exceptions.State
import com.es.camera_2.manager.extensions.HandlerElement
import com.es.camera_2.utils.CompareSizesByArea
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.content.Context.CAMERA_SERVICE
import android.graphics.drawable.ColorDrawable
import android.support.v4.content.ContextCompat.getSystemService
import android.hardware.camera2.CameraManager
import android.media.Image
import android.os.Build
import android.view.View
import java.io.*
import java.nio.ByteBuffer


class CameraPresenter: CameraContract.Presenter, CoroutineScope {

    private val TAG = "CameraPresenter"

    private lateinit var view: CameraContract.View

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    /**
     * An [ImageReader] that handles still image capture.
     */
    private var imageReader: ImageReader? = null

    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0


    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * A reference to the opened [CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null


    private lateinit var cameraManager: CameraManager


    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        @RequiresPermission(Manifest.permission.CAMERA)
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    }

    /**
     * This is the output file for our picture.
     */
    private lateinit var file: File

    /**
     * Whether the current camera device supports Flash or not.
     */
    private var flashSupported = false

    /**
     * ID of the current [CameraDevice].
     */
    private lateinit var cameraId: String

    /**
     * [CaptureRequest] generated by [.previewRequestBuilder]
     */
    private lateinit var previewRequest: CaptureRequest

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private var captureSession: CameraCaptureSession? = null

    private var lensFacing = CameraCharacteristics.LENS_FACING_BACK

    private var isFlash = false

    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener {
        Log.i("onImageAvailable" ,"!!")
        launch(Dispatchers.IO +job) {
            withCamContext{

//                val mFile = createFile()
//                ImageSaver(it.acquireNextImage(), mFile)

                ImageSaver(it, createFile()).saveFile()

            }
        }

    }



    override fun setView(view: CameraContract.View) {
        this.view = view
        createFile()
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    override fun onResume() {
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener)
        reOpenCamera()
    }

   private fun createFile(): File {
        file = view.createFile("", "visual_" + System.currentTimeMillis())
       return file
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun reOpenCamera(){
        view.getTextureView().let {

            if (it.isAvailable) {
                openCamera(it.width, it.height)
            } else {
                it.surfaceTextureListener = surfaceTextureListener
            }
        }
    }

    @RequiresApi(21)
    @RequiresPermission(Manifest.permission.CAMERA)
    private fun openCamera(width: Int, height: Int) {

        val manager = view.getCameraManager()

        manager?: return
        cameraManager = manager

        setUpCameraOutputs(width, height, manager)
        configureTransform(width, height)

        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }

            launch(coroutineContext) {
                withCamContext {
                    manager.withOpenCamera(cameraId) {}
                }
            }


        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }

    }

    suspend fun withCamContext(block: suspend CoroutineScope.() -> Unit) {
        val handlerThread = HandlerThread("cam")
        try {
            handlerThread.start()
            val handler = Handler(handlerThread.looper)
            @Suppress("DEPRECATION")
            (withContext(handler.asCoroutineDispatcher() + HandlerElement(handler), block))
        } finally {
            handlerThread.quitSafely()
        }
    }

    private fun setUpCameraOutputs(width: Int, height: Int, manager: CameraManager) {

        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null &&
                    cameraDirection != lensFacing) {
                    continue
                }

                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                // For still image captures, we use the largest available size.
                val largest = Collections.max(
                    Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                    CompareSizesByArea()
                )

                imageReader = ImageReader.newInstance(largest.width, largest.height,
                    ImageFormat.JPEG, /*maxImages*/ 10).apply {
//                    launch {
//                        withCamContext {
                            setOnImageAvailableListener(onImageAvailableListener, null)
//                        }
//                    }
                }

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                var defaultDisplay = view.getDisplay()

                defaultDisplay ?: return

                val displayRotation = defaultDisplay.rotation

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                val swappedDimensions = areDimensionsSwapped(displayRotation)

                val displaySize = Point()
                defaultDisplay.getSize(displaySize)
                val rotatedPreviewWidth = if (swappedDimensions) height else width
                val rotatedPreviewHeight = if (swappedDimensions) width else height
                var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
                var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = CameraFragment.chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture::class.java),
                    rotatedPreviewWidth, rotatedPreviewHeight,
                    maxPreviewWidth, maxPreviewHeight,
                    largest
                )

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                if (view.getResources()?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    view.setAspectRatioTextureView(previewSize.width, previewSize.height)
                } else {
                    view.setAspectRatioTextureView(previewSize.height, previewSize.width)
                }



                // Check if the flash is supported.
                flashSupported =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                Log.i("flashSupported",""+flashSupported)
                this@CameraPresenter.cameraId = cameraId

                // We've found a viable camera and finished setting up member variables,
                // so we don't need to iterate through other available cameras.
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            view.showErrorDialog()
        }

    }

    /**
     * Determines if the dimensions are swapped given the phone's current rotation.
     *
     * @param displayRotation The current rotation of the display
     *
     * @return true if the dimensions are swapped, false otherwise.
     */
    private fun areDimensionsSwapped(displayRotation: Int): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Log.e(TAG, "Display rotation is invalid: $displayRotation")
            }
        }
        return swappedDimensions
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val display = view.getDisplay()
        display ?: return
        val rotation = display.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        view.setTransformTextureView(matrix)
    }


    @RequiresApi(21)
    @RequiresPermission(Manifest.permission.CAMERA)
    suspend fun <R> CameraManager.withOpenCamera(
        cameraId: String,
        block: suspend CoroutineScope.(CameraDevice) -> R
    ): R = coroutineScope {
        val closedAsync = CompletableDeferred<Unit>()
        val openedCameraAsync = CompletableDeferred<CameraDevice>(coroutineContext[Job])
        val completionAsync = CompletableDeferred<Unit>(coroutineContext[Job])
        val cameraUsageAsync: Deferred<R> = async(start = CoroutineStart.LAZY) {
            openedCameraAsync.await().use { cameraDevice ->
                block(cameraDevice)
            }
        }
        val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.i("onOpened", "camera : $camera")
                cameraDevice = camera
                createCameraPreviewSession()
                cameraOpenCloseLock.release()
//                openedCameraAsync.complete(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.i("onDisconnected", "camera : $camera")
                this@CameraPresenter.cameraDevice = null
                cameraOpenCloseLock.release()
                completionAsync.completeExceptionally(CamStateException(State.Disconnected))
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.i("onError", "camera : $camera $error")
                onDisconnected(camera)
                completionAsync.completeExceptionally(CamStateException(State.Error(error)))
                view.finish()
            }

            override fun onClosed(camera: CameraDevice) {
                Log.i("onClosed", "camera : $camera")
                closedAsync.complete(Unit)
            }
        }

        openCamera(cameraId, stateCallback, null)

        try {
            cameraUsageAsync.await()
        } finally {
            closedAsync.await()
        }
    }


    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = view.getSurfaceTexture()

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )
            previewRequestBuilder.addTarget(surface)

            // crash ending message to a Handler on a dead thread
            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader?.surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (cameraDevice == null) return

                        // When the session is ready, we start displaying the preview.
                        captureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            // Flash is automatically enabled when necessary.
                            setAutoFlash(previewRequestBuilder)

                            // Finally, we start displaying the camera preview.
                            previewRequest = previewRequestBuilder.build()

                            captureSession?.setRepeatingRequest(previewRequest, captureCallback, null)

                        } catch (e: CameraAccessException) {
                            Log.e(TAG, e.toString())
                        }

                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        view.showToast("Failed")
                    }
                }, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
//        if (flashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)

//        }
    }

    private fun setFlash() {
        previewRequestBuilder.let{
            if(!isFlash) {
                it.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                it.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
            } else {
                it.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                it.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            previewRequest = it.build()

            captureSession?.let { session ->
                session.setRepeatingRequest(previewRequest, null, null)
                isFlash = !isFlash
            }
        }
    }




    /**
     * The current state of camera state for taking pictures.
     *
     * @see .captureCallback
     */
    private var state = CameraFragment.STATE_PREVIEW

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {
            when (state) {
                CameraFragment.STATE_PREVIEW -> Unit // Do nothing when the camera preview is working normally.
                CameraFragment.STATE_WAITING_LOCK -> launch(Dispatchers.Main + job){
//                        withCamContext {
                            capturePicture(result)
//                        }
                    }
                CameraFragment.STATE_WAITING_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        state = CameraFragment.STATE_WAITING_NON_PRECAPTURE
                    }
                }
                CameraFragment.STATE_WAITING_NON_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = CameraFragment.STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        private fun capturePicture(result: CaptureResult) {
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            if (afState == null) {
                state = CameraFragment.STATE_PICTURE_TAKEN //add*
                captureStillPicture()
            } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                // CONTROL_AE_STATE can be null on some devices
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    state = CameraFragment.STATE_PICTURE_TAKEN
                    captureStillPicture()
                } else {
                    runPrecaptureSequence()
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession,
                                         request: CaptureRequest,
                                         partialResult: CaptureResult) {
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult) {
            process(result)
        }

    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * [.captureCallback] from both [.lockFocus].
     */
    private fun captureStillPicture() {
        try {
            val display = view.getDisplay()
            if (display == null || cameraDevice == null) return
            val rotation = display.rotation

            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {
                addTarget(imageReader?.surface)

                // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
                // We have to take that into account and rotate JPEG properly.
                // For devices with orientation of 90, we return our mapping from ORIENTATIONS.
                // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
                set(CaptureRequest.JPEG_ORIENTATION,
                    (CameraFragment.ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360)

                // Use the same AE and AF modes as the preview.
                set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }?.also { setAutoFlash(it) }

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult) {
                    view.showToast("Saved: $file")
                    Log.d(TAG, file.toString())
                    unlockFocus()
                }
            }

            captureSession?.apply {
                stopRepeating()
                abortCaptures()
                capture(captureBuilder?.build(), captureCallback, null)
            }

        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private fun unlockFocus() {
        try {
            // Reset the auto-focus trigger
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            setAutoFlash(previewRequestBuilder)

            captureSession?.capture(previewRequestBuilder.build(), captureCallback,
                null)
            // After this, the camera will go back to the normal state of preview.
            state = CameraFragment.STATE_PREVIEW
            captureSession?.setRepeatingRequest(previewRequest, captureCallback,
                null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START)
            // Tell #captureCallback to wait for the lock.
            state = STATE_WAITING_LOCK

            launch(Dispatchers.Main + job) {

//                captureStillPicture()
                captureSession?.capture(previewRequestBuilder.build(), captureCallback,
                    null)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }


    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in [.captureCallback] from [.lockFocus].
     */
    private fun runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            // Tell #captureCallback to wait for the precapture sequence to be set.
            state = CameraFragment.STATE_WAITING_PRECAPTURE
            async(Dispatchers.Main + job) {
                captureSession?.capture(previewRequestBuilder.build(), captureCallback,
                    null)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    override fun onDestroy() {
        job.cancel()
    }

    override fun onClickCaptureButton() {
        lockFocus()

        view.setBackgroundColor()
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    override fun onClickSwitchButton() {
        switchCamera()
    }

    override fun onClickFlashButton() {
        setFlash()
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun switchCamera() {

        if(lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            lensFacing = CameraCharacteristics.LENS_FACING_FRONT
            cameraDevice?.close()
            view.setVisibleFlashBtn(View.GONE)
            reOpenCamera()

        } else if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            lensFacing = CameraCharacteristics.LENS_FACING_BACK
            cameraDevice?.close()
            view.setVisibleFlashBtn(View.VISIBLE)
            reOpenCamera()
        }
    }

}