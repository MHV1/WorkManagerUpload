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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ImageUploadWorker : Worker() {

    private var mNotificationId: Int = 0
    private var mNotificationManager: NotificationManager? = null
    private var mNotificationBuilder: NotificationCompat.Builder? = null

    override fun doWork(): Worker.Result {
        val inputUri = inputData.getString(KEY_IMAGE_URI, "")
        Log.d(TAG, "Uploading image: $inputUri")

        if (inputUri.isNullOrEmpty()) {
            Log.e(TAG, "Invalid or null image URI")
            return Result.FAILURE
        }

        val imageUri = Uri.parse(inputUri)
        val imageFile = File(imageUri.path)

        mNotificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                    "upload_channel_id",
                    "Upload Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            )

            mNotificationManager!!.createNotificationChannel(channel)
        }

        notifyTaskStarted()

        /*try {
            val restResponse: RestResponse? = upload(URL(""), imageFile)
            return if (restResponse != null && restResponse.wasOk()) {
                val successMessage = restResponse.asString

                outputData = Data.Builder()
                        .putString(KEY_IMAGE_URI, successMessage)
                        .build()

                mNotificationManager!!.cancel(mNotificationId)
                Result.SUCCESS
            } else {
                notifyTaskError()
                Result.FAILURE
            }
        } catch (e: IOException) {
            Log.e(TAG, "An error has occurred while uploading file")
            notifyTaskError()
            Result.FAILURE
        }*/

        val response = mockUpload()
        return if (response != null && response.wasOk()) {
            val successMessage = response.asString

            outputData = Data.Builder()
                    .putString(KEY_IMAGE_URI, successMessage)
                    .build()

            mNotificationManager!!.cancel(mNotificationId)
            Worker.Result.SUCCESS
        } else {
            if (isCancelled) {
                Worker.Result.FAILURE
            } else{
                notifyTaskError()
                Worker.Result.FAILURE
            }
        }
    }

    private fun mockUpload(): RestResponse? {
        Log.d(TAG, "mockUpload")
        return try {
            for (i in 1..9) {
                val progress = i * 10
                Log.d(TAG, "Upload progress: $progress")
                notifyTaskProgress(100, progress.toLong());
                if (isCancelled) {
                    notifyTaskCancelled()
                    throw InterruptedException()
                }
                Thread.sleep(1000)
            }
            RestResponse(HttpsURLConnection.HTTP_OK, "OK")
        } catch (e: InterruptedException) {
            null
        }
    }

    @Throws(IOException::class)
    private fun upload(url: URL, imageFile: File): RestResponse? {
        if (!imageFile.exists()) {
            return null
        }

        val startTime = System.currentTimeMillis()

        Log.d(TAG, "Opening HTTP connection...")
        val connection = url.openConnection() as HttpsURLConnection

        Log.d(TAG, "Connection is open")
        connection.requestMethod = "POST"
        connection.doOutput = true

        Log.d(TAG, "Writing request body...")
        val outputStream = connection.outputStream

        val maxBufferSize = 1 * 1024 * 1024

        val inputStream = FileInputStream(imageFile)
        val totalBytes = inputStream.available()
        val buffer = ByteArray(Math.min(totalBytes, maxBufferSize))

        val notificationNumber = 20
        var currentNotification = 0

        var bytesRead: Int
        var uploadedBytes: Long = 0

        while (inputStream.read(buffer) != -1) {
            bytesRead = inputStream.read(buffer)
            outputStream.write(buffer, 0, bytesRead)
            uploadedBytes += bytesRead.toLong()

            val newNotification = (uploadedBytes.toDouble()
                    * notificationNumber.toDouble() * 1.0 / totalBytes).toInt()

            if (newNotification != currentNotification) {
                notifyTaskProgress(totalBytes.toLong(), uploadedBytes);
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

    private fun notifyTaskStarted() {
        Log.d(TAG, "Building notification with Id: $mNotificationId")

        mNotificationBuilder = NotificationCompat.Builder(applicationContext, "upload_channel_id")
                .setWhen(System.currentTimeMillis())
                .setGroup("upload_notification_group")
                .setOnlyAlertOnce(true)
                .setContentTitle("Upload")
                .setContentText("Starting upload...")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setProgress(100, 0, true)
                .setAutoCancel(true)
                .setOngoing(true)

        mNotificationManager!!.notify(mNotificationId, mNotificationBuilder!!.build())
    }

    private fun notifyTaskProgress(totalBytes: Long, uploadedBytes: Long) {
        mNotificationBuilder!!
                .setOnlyAlertOnce(true)
                .setContentTitle("Upload")
                .setContentText("Upload in progress")
                .setProgress(totalBytes.toInt(), uploadedBytes.toInt(), false)
                .setOngoing(true)

        mNotificationManager!!.notify(mNotificationId, mNotificationBuilder!!.build())
    }

    fun notifyTaskCancelled() {
        Log.d(TAG, "Upload has been cancelled!")
        mNotificationManager!!.cancel(mNotificationId)
    }

    private fun notifyTaskError() {
        Log.d(TAG, "An error has occurred while uploading")
        mNotificationManager!!.cancel(mNotificationId)

        val notificationBuilder = NotificationCompat.Builder(applicationContext, "upload_channel_id")
                .setWhen(System.currentTimeMillis())
                .setContentTitle("Upload")
                .setContentText("Error while uploading")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(false)

        // This is needed because the main notification used to show progress is ongoing
        // and a new one has to be created to allow the user to dismiss it.
        mNotificationManager!!.notify(mNotificationId + 1, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "ImageUploadWorker"
    }
}
