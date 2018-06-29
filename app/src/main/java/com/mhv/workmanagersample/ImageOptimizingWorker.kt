/*
 * Copyright (C) 2018 Milan Herrera
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mhv.workmanagersample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import java.io.*
import java.util.*

class ImageOptimizingWorker : Worker() {

    override fun doWork(): Result {
        val inputUri = inputData.getString(KEY_IMAGE_URI, "")
        Log.d(TAG, "Optimizing image before uploading: $inputUri")

        if (inputUri.isNullOrEmpty()) {
            Log.e(TAG, "Invalid or null image URI")
            return Result.FAILURE
        }

        val resolver = applicationContext.contentResolver
        val originalBitmap = BitmapFactory.decodeStream(
                resolver.openInputStream(Uri.parse(inputUri)))

        val resultUri = writeBitmapToFile(applicationContext, originalBitmap)
        Log.d(TAG, "Image successfully optimized and saved: " + resultUri.toString())
        outputData = Data.Builder().putString(
                KEY_IMAGE_URI, resultUri.toString()).build()

        return Result.SUCCESS
    }

    private fun getOriginalImageDimens(imageFilePath: String): IntArray {
        val width: Int
        val height: Int

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        BitmapFactory.decodeFile(imageFilePath, options)

        // Some Samsung and Huawei devices' cameras capture save all images
        // with landscape dimensions regardless of their actual orientation.
        // These images are rotated if needed to show them in portrait mode.
        // Here we check if there is any rotation applied to the picture or not.
        var rotationAngle = 0
        val exif: ExifInterface
        try {
            exif = ExifInterface(imageFilePath)
            val orientString = exif.getAttribute(ExifInterface.TAG_ORIENTATION)
            val orientation = if (orientString != null)
                Integer.parseInt(orientString)
            else
                ExifInterface.ORIENTATION_NORMAL

            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                rotationAngle = 90
            }

        } catch (e: IOException) {
            Log.e(TAG, "ExifInterface not available", e)
            width = options.outWidth
            height = options.outHeight

            return intArrayOf(width, height)
        }

        if (rotationAngle == 90) {
            width = options.outHeight
            height = options.outWidth
        } else {
            width = options.outWidth
            height = options.outHeight
        }

        return intArrayOf(width, height)
    }

    private fun getResizedImageSize(imageFilePath: String, width: Int, height: Int): Long {
        val options = BitmapFactory.Options()
        val originalBitmap = BitmapFactory
                .decodeFile(imageFilePath, options)

        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false)

        val byteArrayOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)

        val compressedImageBytes = byteArrayOutputStream.toByteArray()
        return compressedImageBytes.size.toLong()
    }

    private fun getResizedImageDimens(originalWidth: Int, originalHeight: Int): IntArray {
        val resizedDimensions: IntArray

        if (originalWidth > originalHeight) {
            val resizedHeight = originalHeight.toDouble() / originalWidth.toDouble() * MAX_IMAGE_DIMENSION.toDouble()
            resizedDimensions = intArrayOf(MAX_IMAGE_DIMENSION, resizedHeight.toInt())

        } else if (originalHeight > originalWidth) {
            val resizedWidth = originalWidth.toDouble() / originalHeight.toDouble() * MAX_IMAGE_DIMENSION.toDouble()
            resizedDimensions = intArrayOf(resizedWidth.toInt(), MAX_IMAGE_DIMENSION)

        } else {
            resizedDimensions = intArrayOf(MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
        }

        return resizedDimensions
    }

    @Throws(FileNotFoundException::class)
    private fun writeBitmapToFile(applicationContext: Context, bitmap: Bitmap): Uri {
        val name = String.format("image-optimize-output-%s.jpg", UUID.randomUUID().toString())
        val outputDir = File(applicationContext.filesDir, OUTPUT_PATH)

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(outputDir, name)
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(outputFile)

            var imageDimensions = getOriginalImageDimens(outputFile.absolutePath)
            var imageSize = outputFile.length()

            var imageWidth = imageDimensions[0]
            var imageHeight = imageDimensions[1]

            if (imageWidth > MAX_IMAGE_DIMENSION || imageHeight > MAX_IMAGE_DIMENSION) {
                Log.d(TAG, "Image is to large! Calculating optimal size")
                imageDimensions = getResizedImageDimens(imageWidth, imageHeight)

                imageWidth = imageDimensions[0]
                imageHeight = imageDimensions[1]

                Log.d(TAG, "Image new dimensions: " + imageWidth + "x" + imageHeight)
                imageSize = getResizedImageSize(outputFile.absolutePath, imageWidth, imageHeight)
            }

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, imageWidth, imageHeight, false)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, out)
        } finally {
            if (out != null) {
                try {
                    out.close()
                } catch (ignore: IOException) {
                }
            }
        }

        return Uri.fromFile(outputFile)
    }

    companion object {
        const val TAG = "ImageOptimizingWorker"

        const val MAX_IMAGE_DIMENSION = 2000
    }
}