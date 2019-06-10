package com.es.camera_2

import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.support.v4.content.LocalBroadcastManager
import android.view.Display
import android.view.MotionEvent
import com.es.camera_2.utils.AutoFitTextureView
import java.io.File

interface CameraContract {

    interface View{

        fun getCameraManager(): CameraManager?

        fun getDisplay(): Display?

        fun setAspectRatioTextureView(width: Int, height: Int)

        fun getResources(): Resources?

        fun createFile(dirName: String, fileName: String): File

        fun showErrorDialog()

        fun setTransformTextureView(matrix: Matrix)

        fun finish()

        fun showToast(msg: String)

        fun getSurfaceTexture(): SurfaceTexture

        fun getTextureView(): AutoFitTextureView

        fun setVisibleFlashBtn(visible: Int)

        fun setBackgroundColor()

        fun simulateClick()
    }

    interface Presenter {

        fun setView(view: View)

        fun onClickCaptureButton()

        fun onClickSwitchButton()

        fun onClickFlashButton()

        fun onMultiTouchTextureView(event: MotionEvent)

        fun onResume()

        fun onDestroy()

        fun onViewCreated(localBroadcastManager: LocalBroadcastManager)

        fun onPause()

    }
}