/*
 * Copyright (C) 2021 The Android Open Source Project
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
package me.ranko.autodark.ui

import android.Manifest.permission
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.android.wallpaper.model.ImageWallpaperInfo
import com.android.wallpaper.picker.BasePreviewActivity
import com.android.wallpaper.picker.ImagePreviewFragment
import me.ranko.autodark.R
import me.ranko.autodark.model.CroppedWallpaperInfo
import timber.log.Timber

/**
 * Activity that displays a preview of a specific wallpaper and provides the ability to set the
 * wallpaper as the user's current wallpaper. It's "standalone" meaning it doesn't reside in the
 * app navigation hierarchy and can be launched directly via an explicit intent.
 *
 * [0ranko0P]: Rewrite to kotlin.
 */
class StandalonePreviewActivity : BasePreviewActivity(), ImagePreviewFragment.WallPaperPickerListener {

    companion object {
        private const val READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 1

        const val REQUEST_CODE_PICKER = 257
        const val ARG_WALLPAPER= "wp"
        const val ARG_NO_DESTINATION = "noDest"

        /**
         * Start StandalonePreviewActivity
         *
         * @param data          The content URI from Image picker
         * @param noDestination Force destination to {@code DEST_BOTH}, parse true when old wallpaper is
         *                      Live Wallpaper.
         * */
        @JvmStatic
        fun startActivity(activity: Activity, data: Uri, noDestination: Boolean) {
            val intent = Intent(activity, StandalonePreviewActivity::class.java)
            intent.data = data
            intent.putExtra(ARG_NO_DESTINATION, noDestination)
            activity.startActivityForResult(intent, REQUEST_CODE_PICKER)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val cropAndSetWallpaperIntent: Intent = intent
        val imageUri: Uri? = cropAndSetWallpaperIntent.data

        if (imageUri == null) {
            Timber.e("No URI passed in intent; exiting StandalonePreviewActivity %s", referrer?.host)
            finish()
            return
        }
        window.navigationBarColor = getColor(R.color.bottom_sheet_background)

        // Check if READ_EXTERNAL_STORAGE permission is needed because the app invoking this activity
        // passed a file:// URI or a content:// URI without a flag to grant read permission.
        val isReadPermissionGrantedForImageUri: Boolean = isReadPermissionGrantedForImageUri(imageUri)

        // Request storage permission if necessary (i.e., on Android M and later if storage permission
        // has not already been granted) and delay loading the PreviewFragment until the permission is
        // granted.
        if (!isReadPermissionGrantedForImageUri && !isReadExternalStoragePermissionGrantedForApp()) {
            requestPermissions(arrayOf(permission.READ_EXTERNAL_STORAGE), READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Load the preview fragment if the storage permission was granted.
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE) {
            val isGranted =
                    (permissions.isNotEmpty() && permissions[0] == permission.READ_EXTERNAL_STORAGE
                            && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)

            // Close the activity because we can't open the image without storage permission.
            if (!isGranted) {
                finish()
            }
            loadPreviewFragment()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val fragmentManager: FragmentManager = supportFragmentManager
        val fragment: Fragment? = fragmentManager.findFragmentById(R.id.fragment_container)

        if (fragment == null) {
            loadPreviewFragment()
        }
    }

    private fun loadPreviewFragment() {
        val noDest = intent.getBooleanExtra(ARG_NO_DESTINATION, false)
        val fragment = ImagePreviewFragment.newInstance(ImageWallpaperInfo(intent.data), noDest)
        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, fragment)
                .commit()
    }

    /**
     * Returns whether the user has granted READ_EXTERNAL_STORAGE permission to the app.
     */
    private fun isReadExternalStoragePermissionGrantedForApp(): Boolean {
        return packageManager.checkPermission(permission.READ_EXTERNAL_STORAGE, packageName) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns whether the provided image Uri is readable without requiring the app to have the user
     * grant READ_EXTERNAL_STORAGE permission.
     */
    private fun isReadPermissionGrantedForImageUri(imageUri: Uri): Boolean {
        return checkUriPermission(imageUri, Binder.getCallingPid(), Binder.getCallingUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onWallpaperCropped(wallpaperInfo: CroppedWallpaperInfo?) {
        val intent = Intent()
        intent.putExtra(ARG_WALLPAPER, wallpaperInfo)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}