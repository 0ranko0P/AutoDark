package me.ranko.autodark.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import com.android.wallpaper.picker.BasePreviewActivity
import me.ranko.autodark.R
import me.ranko.autodark.model.CroppedWallpaperInfo
import me.ranko.autodark.ui.DarkWallpaperPickerViewModel.WallpaperRequest
import me.ranko.autodark.ui.StandalonePreviewActivity.Companion.ARG_WALLPAPER
import timber.log.Timber

class DarkWallpaperPickerActivity : BasePreviewActivity() {

    companion object {
        private const val REQUEST_PICK_IMAGE = 233

        fun storageGranted(context: Context): Boolean {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED
        }
    }

    private lateinit var viewModel: DarkWallpaperPickerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, DarkWallpaperPickerViewModel.Companion.Factory(application)).get()
        lifecycle.addObserver(viewModel)
        setContentView(R.layout.activity_preview)

        viewModel.requestPermissions.observe(this, { request ->
            if (request) onRequestPermission() else onAllPermissionHandled()
        })
    }

    private fun onRequestPermission() {
        if (supportFragmentManager.findFragmentByTag(DarkWallpaperPermissionFragment.TAG) != null) return

        val migrating = storageGranted(this)
        supportFragmentManager.beginTransaction()
            .add(
                R.id.fragment_container,
                DarkWallpaperPermissionFragment.instance(migrating),
                DarkWallpaperPermissionFragment.TAG
            )
            .commit()
    }

    private fun onAllPermissionHandled() {
        with(supportFragmentManager) {
            val permissionFrag = findFragmentByTag(DarkWallpaperPermissionFragment.TAG)
            if (permissionFrag != null) {
                beginTransaction().remove(permissionFrag).commit()
            }
            if (findFragmentByTag(DarkWallpaperFragment.TAG) == null) {
                beginTransaction()
                    .replace(
                        R.id.fragment_container,
                        DarkWallpaperFragment(),
                        DarkWallpaperFragment.TAG
                    )
                    .commit()
            }
        }

        viewModel.wallpaperPickRequest.observe(this, { request ->
            if (request == WallpaperRequest.STATIC_WALLPAPER) {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                startActivityForResult(
                    Intent.createChooser(intent, "Select Picture"),
                    REQUEST_PICK_IMAGE
                )
            }
        })
    }

    override fun onBackPressed() {
        if (viewModel.wallpaperPickRequest.value == WallpaperRequest.LIVE_WALLPAPER) {
            viewModel.onDismissLiveWallpaperPicker()
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_PICK_IMAGE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val noDestination = viewModel.isNoDestination()
                    StandalonePreviewActivity.startActivity(this, data!!.data!!, noDestination)
                } else {
                    viewModel.onDismissWallpaperPicker()
                }
            }

            StandalonePreviewActivity.REQUEST_CODE_PICKER -> {
                if (resultCode == Activity.RESULT_OK) {
                    val wallpaper = data!!.getParcelableExtra<CroppedWallpaperInfo>(ARG_WALLPAPER)!!
                    Timber.d("new wallpaper: %s", wallpaper)
                    viewModel.onWallpaperPicked(wallpaper)
                } else {
                    viewModel.onDismissWallpaperPicker()
                }
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }
}