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

import android.Manifest
import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import androidx.work.WorkStatus
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: UploadViewModel

    private var mPermissionRequestCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProviders.of(this).get(UploadViewModel::class.java)
        viewModel.outputStatus.observe(this, Observer<List<WorkStatus>> { listOfStatuses ->
            if (listOfStatuses == null || listOfStatuses.isEmpty()) {
                return@Observer
            }

            val status = listOfStatuses[0]
            val finished = status.state.isFinished
            if (!finished) {
                Log.d(TAG, "Work ongoing...")
            } else {
                val outputData = status.outputData
                val output = outputData.getString(KEY_IMAGE_URI, null)
                Log.d(TAG, "Work successful: " + output!!)
            }
        })

        // We keep track of the number of times we requested for permissions.
        // If the user did not want to grant permissions twice - show a Toast and don't
        // ask for permissions again for the rest of the session
        if (savedInstanceState != null) {
            mPermissionRequestCount = savedInstanceState.getInt(KEY_PERMISSIONS_REQUEST_COUNT, 0)
        }

        requestPermissionsIfNecessary()

        select_image_button.setOnClickListener {
            val chooseIntent = Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(chooseIntent, REQUEST_CODE_IMAGE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PERMISSIONS_REQUEST_COUNT, mPermissionRequestCount)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_IMAGE -> handleImageRequestResult(data)
                else -> Log.d(TAG, "Unknown request code")
            }
        } else {
            Log.e(TAG, "Unexpected Result code $resultCode")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            requestPermissionsIfNecessary()
        }
    }

    private fun requestPermissionsIfNecessary() {
        if (!checkAllPermissions()) {
            if (mPermissionRequestCount < MAX_NUMBER_REQUEST_PERMISSIONS) {
                mPermissionRequestCount += 1
                ActivityCompat.requestPermissions(this,
                        sPermissions.toTypedArray(),
                        REQUEST_CODE_PERMISSIONS)
            } else {
                Toast.makeText(this, """Go to Settings -> Apps and Notifications
                    -> WorkManagerUpload -> App Permissions and grant access to Storage.""",
                        Toast.LENGTH_SHORT).show()

                select_image_button.isEnabled = false
            }
        }
    }

    private fun handleImageRequestResult(data: Intent) {
        var imageUri: Uri? = null
        if (Build.VERSION.SDK_INT >= 16 && data.clipData != null) {
            imageUri = data.clipData!!.getItemAt(0).uri
        } else if (data.data != null) {
            imageUri = data.data
        }

        if (imageUri == null) {
            Log.e(TAG, "Invalid input image Uri")
            return
        }

        viewModel.upload(imageUri.toString())
    }

    private fun checkAllPermissions(): Boolean {
        var hasPermissions = true
        for (permission in sPermissions) {
            hasPermissions = hasPermissions and (ContextCompat.checkSelfPermission(
                    this, permission) == PackageManager.PERMISSION_GRANTED)
        }
        return hasPermissions
    }

    companion object {

        private const val TAG = "MainActivity"

        private const val KEY_PERMISSIONS_REQUEST_COUNT = "KEY_PERMISSIONS_REQUEST_COUNT"

        private const val MAX_NUMBER_REQUEST_PERMISSIONS = 2
        private const val REQUEST_CODE_IMAGE = 100
        private const val REQUEST_CODE_PERMISSIONS = 101

        private val sPermissions = object : ArrayList<String>() {
            init {
                add(Manifest.permission.INTERNET)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        init {
            if (Build.VERSION.SDK_INT >= 16) {
                sPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

