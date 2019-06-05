package com.es.camera_2

import android.Manifest
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.camera2.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.annotation.RequiresPermission
import android.support.constraint.ConstraintLayout
import android.support.v4.app.Fragment
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageButton
import com.es.camera_2.dialog.ErrorDialog
import com.es.camera_2.manager.extensions.HandlerElement
import com.es.camera_2.utils.ANIMATION_FAST_MILLIS
import com.es.camera_2.utils.ANIMATION_SLOW_MILLIS
import com.es.camera_2.utils.CompareSizesByArea
import com.es.camera_2.utils.showToast
import kotlinx.coroutines.*
import java.io.File
import java.lang.Exception
import java.util.*


class CameraFragment : Fragment(), View.OnClickListener, CameraContract.View{

    private val TAG = "CameraFragment"

    private lateinit var textureView: AutoFitTextureView

    private var presenter: CameraContract.Presenter = CameraPresenter()

    private lateinit var flashButton: ImageButton

    private lateinit var container: ConstraintLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        flashButton = view.findViewById(R.id.flash_button)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flashButton.visibility = View.VISIBLE
            flashButton.setOnClickListener(this)
        } else {
            flashButton.visibility = View.GONE
        }

        container = view.findViewById(R.id.camera_container)
        textureView = view.findViewById(R.id.texture)
        presenter.setView(this)

    }


    override fun setVisibleFlashBtn(visible: Int) {
        flashButton.visibility = visible
    }

    override fun getCameraManager(): CameraManager? {
        return activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun getDisplay(): Display? {
        return activity?.windowManager?.defaultDisplay
    }

    override fun setAspectRatioTextureView(width: Int, height: Int) {
        textureView.setAspectRatio(width, height)
    }

    override fun createFile(dirName: String, fileName: String): File {
        return File(activity?.getExternalFilesDir(null), fileName)
    }

    override fun showErrorDialog() {
        ErrorDialog.newInstance(getString(R.string.camera_error))
        .show(childFragmentManager, "dialog")
    }

    override fun setTransformTextureView(matrix: Matrix) {
        textureView.setTransform(matrix)
    }

    override fun finish() {
        activity?.finish()
    }

    override fun showToast(msg: String) {
        activity?.showToast(msg)
    }

    override fun getSurfaceTexture(): SurfaceTexture {
        return textureView.surfaceTexture
    }

    override fun getTextureView(): AutoFitTextureView {
        return textureView
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.camera_capture_button -> presenter.onClickCaptureButton()
            R.id.camera_switch_button -> presenter.onClickSwitchButton()
            R.id.flash_button -> presenter.onClickFlashButton()
        }
    }



    companion object {

        val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }


        /**
         * Max preview that is guaranteed by Camera2 API
         */
        val MAX_PREVIEW_WIDTH = 1920

        val MAX_PREVIEW_HEIGHT = 1080


        /**
         * Camera state: Showing camera preview.
         */
        val STATE_PREVIEW = 0

        /**
         * Camera state: Waiting for the focus to be locked.
         */
        val STATE_WAITING_LOCK = 1

        /**
         * Camera state: Waiting for the exposure to be precapture state.
         */
        val STATE_WAITING_PRECAPTURE = 2

        /**
         * Camera state: Waiting for the exposure state to be something other than precapture.
         */
        val STATE_WAITING_NON_PRECAPTURE = 3

        /**
         * Camera state: Picture was taken.
         */
        val STATE_PICTURE_TAKEN = 4

        @JvmStatic
        fun newInstance() :CameraFragment = CameraFragment()

//        @JvmStatic fun chooseOptimalSize(
//            choices: Array<Size>,
//            textureViewWidth: Int,
//            textureViewHeight: Int,
//            maxWidth: Int,
//            maxHeight: Int,
//            aspectRatio: Size
//        ): Size {
//
//            // Collect the supported resolutions that are at least as big as the preview Surface
//            val bigEnough = ArrayList<Size>()
//            // Collect the supported resolutions that are smaller than the preview Surface
//            val notBigEnough = ArrayList<Size>()
//            val w = aspectRatio.width
//            val h = aspectRatio.height
//            for (option in choices) {
//                if (option.width <= maxWidth && option.height <= maxHeight &&
//                    option.height == option.width * h / w) {
//                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
//                        bigEnough.add(option)
//                    } else {
//                        notBigEnough.add(option)
//                    }
//                }
//            }
//
//            // Pick the smallest of those big enough. If there is no one big enough, pick the
//            // largest of those not big enough.
//            return if (bigEnough.size > 0) {
//                Collections.min(bigEnough, CompareSizesByArea())
//            } else if (notBigEnough.size > 0) {
//                Collections.max(notBigEnough, CompareSizesByArea())
//            } else {
//                Log.e("CameraFragment", "Couldn't find any suitable preview size")
//                choices[0]
//            }
//        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    override fun onResume() {
        super.onResume()
        presenter.onResume()
    }

    override fun onDestroy() {
        // Stop the coroutines as the context gets destroyed
        presenter.onDestroy()
        super.onDestroy()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun setBackgroundColor() {
        // We can only change the foreground Drawable using API level 23+ API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // Display flash animation to indicate that photo was captured
            container.postDelayed({
                container.foreground = ColorDrawable(Color.WHITE)
                container.postDelayed({ container.foreground = null }, ANIMATION_FAST_MILLIS)
            }, ANIMATION_SLOW_MILLIS)
        }
    }


}
