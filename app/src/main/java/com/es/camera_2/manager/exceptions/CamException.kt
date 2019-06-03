package com.es.camera_2.manager.exceptions

import android.hardware.camera2.CameraDevice


sealed class CamException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class CamStateException(val nonOpenState: State) : CamException() {

    init {
        require(nonOpenState != State.Opened)
    }

    override val message = "$nonOpenState"
}

class CamCaptureSessionStateException(val state: State.Closed) : CamException() {
    override val message = "$state"
}

sealed class State {
    object Opened : State()
    class Error(val errorCode: Int) : State() {
        fun errorString(): String = when (errorCode) {
            CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "ERROR_CAMERA_IN_USE"
            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "ERROR_MAX_CAMERAS_IN_USE"
            CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "ERROR_CAMERA_DISABLED"
            CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "ERROR_CAMERA_DEVICE"
            CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "ERROR_CAMERA_SERVICE"
            else -> "Unknown error state: $errorCode"
        }

        override fun toString() = errorString()
    }

    object Disconnected : State()
    object Closed : State()
}