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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ImageUploadWorker : Worker() {

    override fun doWork(): Worker.Result {
        val imageUriInput: String?
        val args = inputData
        imageUriInput = args.getString(KEY_IMAGE_URI, null)
        val imageUri = Uri.parse(imageUriInput)
        val imageFile = File(imageUri.path)

        // TODO: Actual upload. Currently are using the mockUpload to test
        /*try {
            final RestResponse response = upload(new URL(""), imageFile);
            if (response != null && response.wasOk()) {
                // TODO: This ideally will be the URL of the uploaded image location
                String successMessage = response.getAsString();

                // Set the result of the worker by calling setOutputData()
                setOutputData(new Data.Builder()
                        .putString(Constants.KEY_IMAGE_URI, successMessage)
                        .build()
                );
                return SUCCESS;
            } else {
                // The user should retry if they wish
                return FAILURE;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while attempting to upload image", e);
            return FAILURE;
        }*/


        val response = mockUpload()
        return if (response != null && response.wasOk()) {
            // TODO: This ideally will be the URL of the uploaded image location
            val successMessage = response.asString

            // Set the result of the worker by calling setOutputData()
            outputData = Data.Builder()
                    .putString(KEY_IMAGE_URI, successMessage)
                    .build()
            Worker.Result.SUCCESS
        } else {
            // The user should retry if they wish
            Worker.Result.FAILURE
        }
    }

    private fun mockUpload(): RestResponse? {
        try {
            for (i in 1..9) {
                val progress = i * 10
                // TODO: Update notification
                Log.d(TAG, "Upload progress: $progress")
                // notifyTaskProgress(100, progress);
                Thread.sleep(1000)
            }
            return RestResponse(HttpsURLConnection.HTTP_OK, "OK")
        } catch (e: InterruptedException) {
            e.printStackTrace()
            return null
        }

    }

    @Throws(IOException::class)
    private fun upload(url: URL, imageFile: File): RestResponse? {
        if (!imageFile.exists()) {
            return null
        }

        // TODO: Do this scaling operation in another job and chain it with the upload one
        val options = BitmapFactory.Options()
        val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)

        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 200, 200, false)

        val byteArrayOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)

        val compressedImageBytes = byteArrayOutputStream.toByteArray()
        Log.d(TAG, "Image bytes - original: " + imageFile.length() + " compressed: "
                + compressedImageBytes.size)

        val startTime = System.currentTimeMillis()

        Log.d(TAG, "Opening HTTP connection...")
        val connection = url.openConnection() as HttpsURLConnection

        Log.d(TAG, "Connection is open")
        connection.requestMethod = "POST"
        connection.doOutput = true

        Log.d(TAG, "Writing request body...")
        val outputStream = connection.outputStream

        val inputStream = ByteArrayInputStream(compressedImageBytes)
        val buffer = ByteArray(compressedImageBytes.size)
        var bytesRead: Int
        val notificationNumber = 20
        var currentNotification = 0

        val totalBytes = compressedImageBytes.size.toLong()
        var uploadedBytes: Long = 0

        while (inputStream.read(buffer) != -1) {
            bytesRead = inputStream.read(buffer)
            outputStream.write(buffer, 0, bytesRead)
            uploadedBytes += bytesRead.toLong()
            val newNotification = (uploadedBytes.toDouble() * notificationNumber.toDouble() * 1.0 / totalBytes).toInt()
            if (newNotification != currentNotification) {
                // TODO: Update notification
                // notifyTaskProgress(totalBytes, uploadedBytes);
                currentNotification = newNotification
            }
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()

        val elapsedTime = System.currentTimeMillis() - startTime
        Log.d(TAG, url.path + " took: " + java.lang.Long.toString(elapsedTime / 1000) + "s")
        return RestResponse(connection.responseCode, connection.responseMessage)
    }

    companion object {

        private val TAG = "ImageUploadWorker"
    }
}
