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

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.util.Log
import androidx.work.*

class UploadViewModel : ViewModel() {

    private val mWorkManager: WorkManager? = WorkManager.getInstance()

    val outputStatus: LiveData<List<WorkStatus>>
        get() = mWorkManager!!.getStatusesByTag(TAG_OUTPUT)

    fun upload(imageUri: String) {
        val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val data = Data.Builder()
                .putString(KEY_IMAGE_URI, imageUri)
                .build()

        val upload = OneTimeWorkRequest.Builder(ImageUploadWorker::class.java)
                .setInputData(data)
                .addTag(TAG_OUTPUT)
                .setConstraints(constraints)
                .build()

        Log.d(TAG, "beginUniqueWork")
        mWorkManager!!.beginUniqueWork(IMAGE_UPLOAD_WORK_NAME,
                ExistingWorkPolicy.REPLACE, upload).enqueue()
    }

    fun cancel() {
        mWorkManager!!.cancelUniqueWork(IMAGE_UPLOAD_WORK_NAME)
    }

    companion object {
        private const val TAG = "UploadViewModel"
    }
}