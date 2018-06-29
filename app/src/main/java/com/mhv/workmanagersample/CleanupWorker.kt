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

import android.text.TextUtils
import android.util.Log
import androidx.work.Worker
import java.io.File

class CleanupWorker : Worker() {
    override fun doWork(): Result {
        Log.d(TAG, "Cleaning up temporary files")
        val applicationContext = applicationContext

        try {
            val outputDirectory = File(applicationContext.filesDir, OUTPUT_PATH)
            if (outputDirectory.exists()) {
                val entries = outputDirectory.listFiles()
                if (entries != null && entries.isNotEmpty()) {
                    for (entry in entries) {
                        val name = entry.name
                        if (!TextUtils.isEmpty(name) && name.endsWith(".jpg")) {
                            val deleted = entry.delete()
                            Log.i(TAG, String.format("Deleted %s - %s", name, deleted))
                        }
                    }
                }
            }
            Log.d(TAG, "Done cleaning!")
            return Result.SUCCESS
        } catch (exception: Exception) {
            Log.e(TAG, "Error cleaning up", exception)
            return Result.FAILURE
        }
    }

    companion object {
        const val TAG = "CleanupWorker"
    }
}