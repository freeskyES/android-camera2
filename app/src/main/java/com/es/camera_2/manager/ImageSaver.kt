package com.es.camera_2.manager

import android.media.Image
import android.media.ImageReader
import android.util.Log
import java.io.*

import java.nio.ByteBuffer

/**
 * Saves a JPEG [Image] into the specified [File].
 */
internal class ImageSaver(
        /**
         * The JPEG image
         */
        private var reader: ImageReader,

        /**
         * The file we save the image into.
         */
        private var file: File
) {


    fun saveFile(){

        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            var buffer: ByteBuffer = image.planes[0].buffer
            var bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            save(bytes)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch ( e: IOException) {
            e.printStackTrace()
        } finally {
            image?.close()
        }


    }
    private fun save(bytes: ByteArray) {
        var output : OutputStream?= null
        try {
            output = FileOutputStream(file).apply { write(bytes) }
        } finally {
            output?.close()
        }
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private val TAG = "ImageSaver"
    }
}
