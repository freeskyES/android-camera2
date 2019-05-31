package com.es.camera_2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.*
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


class CameraFragment : Fragment(), View.OnClickListener, CameraContract.View{

    private lateinit var textureView: AutoFitTextureView

    private var presenter: CameraContract.Presenter = CameraPresenter()

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * A reference to the opened [CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
//            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
//            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    }

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@CameraFragment.cameraDevice = cameraDevice
//            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraFragment.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            this@CameraFragment.activity?.finish()
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.camera_capture_button).setOnClickListener(this)
        view.findViewById<View>(R.id.camera_switch_button).setOnClickListener(this)
        view.findViewById<View>(R.id.flash_button).setOnClickListener(this)
        textureView = view.findViewById(R.id.texture)
        presenter.setView(this)
    }

//    private fun openCamera(width: Int, height: Int) {
//
//        setUpCameraOutputs(width, height)
//        configureTransform(width, height)
//
//        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
//        try {
//            // Wait for camera to open - 2.5 seconds is sufficient
//            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
//                throw RuntimeException("Time out waiting to lock camera opening.")
//            }
//            manager.openCamera(cameraId, stateCallback, backgroundHandler)
//        } catch (e: CameraAccessException) {
//            Log.e(TAG, e.toString())
//        } catch (e: InterruptedException) {
//            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
//        }
//
//    }
//
//    private fun setUpCameraOutputs(width: Int, height: Int) {
//        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
//        try {
//            for (cameraId in manager.cameraIdList) {
//                val characteristics = manager.getCameraCharacteristics(cameraId)
//
//                // We don't use a front facing camera in this sample.
//                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
//                if (cameraDirection != null &&
//                    cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
//                    continue
//                }
//
//                val map = characteristics.get(
//                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
//
//                // For still image captures, we use the largest available size.
//                val largest = Collections.max(
//                    Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
//                    CompareSizesByArea())
//                imageReader = ImageReader.newInstance(largest.width, largest.height,
//                    ImageFormat.JPEG, /*maxImages*/ 2).apply {
//                    setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
//                }
//
//                // Find out if we need to swap dimension to get the preview size relative to sensor
//                // coordinate.
//                val displayRotation = activity.windowManager.defaultDisplay.rotation
//
//                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
//                val swappedDimensions = areDimensionsSwapped(displayRotation)
//
//                val displaySize = Point()
//                activity.windowManager.defaultDisplay.getSize(displaySize)
//                val rotatedPreviewWidth = if (swappedDimensions) height else width
//                val rotatedPreviewHeight = if (swappedDimensions) width else height
//                var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
//                var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y
//
//                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
//                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT
//
//                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
//                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
//                // garbage capture data.
//                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
//                    rotatedPreviewWidth, rotatedPreviewHeight,
//                    maxPreviewWidth, maxPreviewHeight,
//                    largest)
//
//                // We fit the aspect ratio of TextureView to the size of preview we picked.
//                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                    textureView.setAspectRatio(previewSize.width, previewSize.height)
//                } else {
//                    textureView.setAspectRatio(previewSize.height, previewSize.width)
//                }
//
//                // Check if the flash is supported.
//                flashSupported =
//                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
//
//                this.cameraId = cameraId
//
//                // We've found a viable camera and finished setting up member variables,
//                // so we don't need to iterate through other available cameras.
//                return
//            }
//        } catch (e: CameraAccessException) {
//            Log.e(TAG, e.toString())
//        } catch (e: NullPointerException) {
//            // Currently an NPE is thrown when the Camera2API is used but not supported on the
//            // device this code runs.
//            ErrorDialog.newInstance(getString(R.string.camera_error))
//                .show(childFragmentManager, FRAGMENT_DIALOG)
//        }
//
//    }
//
//    /**
//     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
//     * This method should be called after the camera preview size is determined in
//     * setUpCameraOutputs and also the size of `textureView` is fixed.
//     *
//     * @param viewWidth  The width of `textureView`
//     * @param viewHeight The height of `textureView`
//     */
//    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
//        activity ?: return
//        val rotation = activity.windowManager.defaultDisplay.rotation
//        val matrix = Matrix()
//        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
//        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
//        val centerX = viewRect.centerX()
//        val centerY = viewRect.centerY()
//
//        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
//            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
//            val scale = Math.max(
//                viewHeight.toFloat() / previewSize.height,
//                viewWidth.toFloat() / previewSize.width)
//            with(matrix) {
//                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
//                postScale(scale, scale, centerX, centerY)
//                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
//            }
//        } else if (Surface.ROTATION_180 == rotation) {
//            matrix.postRotate(180f, centerX, centerY)
//        }
//        textureView.setTransform(matrix)
//    }
//
//    /**
//     * Creates a new [CameraCaptureSession] for camera preview.
//     */
//    private fun createCameraPreviewSession() {
//        try {
//            val texture = textureView.surfaceTexture
//
//            // We configure the size of default buffer to be the size of camera preview we want.
//            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
//
//            // This is the output Surface we need to start preview.
//            val surface = Surface(texture)
//
//            // We set up a CaptureRequest.Builder with the output Surface.
//            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
//                CameraDevice.TEMPLATE_PREVIEW
//            )
//            previewRequestBuilder.addTarget(surface)
//
//            // Here, we create a CameraCaptureSession for camera preview.
//            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader?.surface),
//                object : CameraCaptureSession.StateCallback() {
//
//                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
//                        // The camera is already closed
//                        if (cameraDevice == null) return
//
//                        // When the session is ready, we start displaying the preview.
//                        captureSession = cameraCaptureSession
//                        try {
//                            // Auto focus should be continuous for camera preview.
//                            previewRequestBuilder.set(
//                                CaptureRequest.CONTROL_AF_MODE,
//                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
//                            // Flash is automatically enabled when necessary.
//                            setAutoFlash(previewRequestBuilder)
//
//                            // Finally, we start displaying the camera preview.
//                            previewRequest = previewRequestBuilder.build()
//                            captureSession?.setRepeatingRequest(previewRequest,
//                                captureCallback, backgroundHandler)
//                        } catch (e: CameraAccessException) {
//                            Log.e(TAG, e.toString())
//                        }
//
//                    }
//
//                    override fun onConfigureFailed(session: CameraCaptureSession) {
//                        activity.showToast("Failed")
//                    }
//                }, null)
//        } catch (e: CameraAccessException) {
//            Log.e(TAG, e.toString())
//        }
//
//    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.camera_capture_button -> presenter.onClickCaptureButton()
            R.id.camera_switch_button -> presenter.onClickSwitchButton()
            R.id.flash_button -> presenter.onClickFlashButton()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() :CameraFragment = CameraFragment()
    }
}
