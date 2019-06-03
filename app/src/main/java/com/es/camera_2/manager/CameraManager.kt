package com.es.camera_2.manager

import android.Manifest
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.support.annotation.RequiresApi
import android.support.annotation.RequiresPermission
import android.util.Log
import com.es.camera_2.manager.exceptions.CamStateException
import com.es.camera_2.manager.exceptions.State
import com.es.camera_2.manager.extensions.requireHandler
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

//@RequiresApi(21)
//@RequiresPermission(Manifest.permission.CAMERA)
//suspend fun <R> CameraManager.withOpenCamera(
//    cameraId: String,
//    block: suspend CoroutineScope.(CameraDevice) -> R
//): R = coroutineScope {
//    val closedAsync = CompletableDeferred<Unit>()
//    val openedCameraAsync = CompletableDeferred<CameraDevice>(coroutineContext[Job])
//    val completionAsync = CompletableDeferred<Unit>(coroutineContext[Job])
//    val cameraUsageAsync: Deferred<R> = async(start = CoroutineStart.LAZY) {
//        openedCameraAsync.await().use { cameraDevice ->
//            block(cameraDevice)
//        }
//    }
//    val stateCallback = object : CameraDevice.StateCallback() {
//        override fun onOpened(camera: CameraDevice) {
//            Log.i("onOpened", "camera : $camera")
//            openedCameraAsync.complete(camera)
//        }
//
//        override fun onDisconnected(camera: CameraDevice) {
//            Log.i("onDisconnected", "camera : $camera")
//            completionAsync.completeExceptionally(CamStateException(State.Disconnected))
//            camera.close()
//        }
//
//        override fun onError(camera: CameraDevice, error: Int) {
//            Log.i("onError", "camera : $camera $error")
//
//            completionAsync.completeExceptionally(CamStateException(State.Error(error)))
//            camera.close()
//        }
//
//        override fun onClosed(camera: CameraDevice) {
//            Log.i("onClosed", "camera : $camera")
//            closedAsync.complete(Unit)
//        }
//    }
//
//    openCamera(cameraId, stateCallback, coroutineContext.requireHandler())
//
//    try {
//        cameraUsageAsync.await()
//    } finally {
//        closedAsync.await()
//    }
//}

//@RequiresApi(21)
//@RequiresPermission(Manifest.permission.CAMERA)
//suspend fun CameraManager.withOpenCamera(cameraId: String): CameraDevice? =
//    suspendCoroutine { cont ->
//        val callback = object : CameraDevice.StateCallback() {
//            override fun onOpened(camera: CameraDevice) {
//                Log.i("onOpened", "camera : $camera")
//                cont.resume(camera)
//            }
//
//            override fun onDisconnected(camera: CameraDevice) {
//                Log.i("onDisconnected", "camera : $camera")
//                cont.resume(null)
//            }
//
//            override fun onError(camera: CameraDevice, error: Int) {
//                Log.i("onOponErrorened", "camera : $camera $error")
//                // assuming that we don't care about the error in this example
//                cont.resume(null)
//            }
//        }
//
//        openCamera(cameraId, callback, null)
//    }
