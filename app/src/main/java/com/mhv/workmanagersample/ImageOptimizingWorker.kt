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
import android.net.Uri
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import java.io.*
import java.util.*

class ImageOptimizingWorker: Worker() {

    override fun doWork(): Result {
        val inputUri = inputData.getString(KEY_IMAGE_URI, "")
        Log.d(TAG, "Optimizing image before uploading: " + inputUri.toString())

        if (inputUri.isNullOrEmpty()) {
            Log.e(TAG, "Invalid or null image URI")
            return Result.FAILURE
        }

        val resolver = applicationContext.contentResolver
        val originalBitmap = BitmapFactory.decodeStream(
                resolver.openInputStream(Uri.parse(inputUri)))

        // TODO: Desired image dimensions should be calculated
        // properly and passed to this Worker
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 200, 200, false)

        val resultUri = writeBitmapToFile(applicationContext, scaledBitmap)
        Log.d(TAG, "Image successfully optimized and saved: " + resultUri.toString())
        outputData = Data.Builder().putString(
                KEY_IMAGE_URI, resultUri.toString()).build()

        return Result.SUCCESS
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
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out)
        } finally {
            if (out != null) {
                try {
                    out.close()
                } catch (ignore: IOException) {}
            }
        }

        return Uri.fromFile(outputFile)
    }

    companion object {
        const val TAG = "ImageOptimizingWorker"
    }
}