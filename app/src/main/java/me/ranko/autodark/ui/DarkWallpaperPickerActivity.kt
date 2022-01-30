package me.ranko.autodark.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import com.android.wallpaper.picker.BasePreviewActivity
import me.ranko.autodark.R
import me.ranko.autodark.model.CroppedWallpaperInfo
import me.ranko.autodark.ui.DarkWallpaperPickerViewModel.WallpaperRequest
import me.ranko.autodark.ui.StandalonePreviewActivity.Companion.ARG_WALLPAPER

import com.android.wallpaper.module.WallpaperPersister.DEST_BOTH

class DarkWallpaperPickerActivity : BasePreviewActivity() {

    companion object {
        fun storageGranted(context: Context): Boolean {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED
        }
    }

    private lateinit var viewModel: DarkWallpaperPickerViewModel

    private lateinit var resultObserver: ActivityResultObserver

    private class ActivityResultObserver(
        private val provider: ViewModelProvider,
        private val registry: ActivityResultRegistry
    ) : DefaultLifecycleObserver {

        private lateinit var pickerLauncher: ActivityResultLauncher<String>
        private lateinit var cropperLauncher: ActivityResultLauncher<Intent>

        override fun onCreate(owner: LifecycleOwner) {
            pickerLauncher = registry.register("Picker", owner, GetContent()) {
                val vm = provider[DarkWallpaperPickerViewModel::class.java]
                if (it != null) {
                    cropImage(vm.getApplication(), it, vm.isNoDestination())
                } else {
                    vm.onDismissWallpaperPicker()
                }
            }

            cropperLauncher = registry.register("Cropper", owner, StartActivityForResult()) {
                val vm = provider[DarkWallpaperPickerViewModel::class.java]
                if (it.resultCode == Activity.RESULT_OK) {
                    val wallpaper = it.data!!.getParcelableExtra<CroppedWallpaperInfo>(ARG_WALLPAPER)!!
                    vm.onWallpaperPicked(wallpaper)
                } else {
                    vm.onDismissWallpaperPicker()
                }
            }
        }

        /**
         * Launch a new activity with image picker intent
         * */
        fun pickImage() {
            pickerLauncher.launch("image/*")
        }

        /**
         * Launch [StandalonePreviewActivity] with picked image
         *
         * @param uri           The content URI from Image picker
         * @param noDestination Force destination to [DEST_BOTH] when
         *                      old wallpaper is Live Wallpaper.
         * */
        fun cropImage(context: Context, uri: Uri, noDestination: Boolean) {
            val intent = Intent(context, StandalonePreviewActivity::class.java)
            intent.data = uri
            intent.putExtra(StandalonePreviewActivity.ARG_NO_DESTINATION, noDestination)
            cropperLauncher.launch(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = ViewModelProvider(this, DarkWallpaperPickerViewModel.Companion.Factory(application))
        viewModel = provider.get()
        lifecycle.addObserver(viewModel)
        resultObserver = ActivityResultObserver(provider, activityResultRegistry)
        lifecycle.addObserver(resultObserver)
        setContentView(R.layout.activity_preview)

        viewModel.requestPermissions.observe(this) { request ->
            if (request) onRequestPermission() else onAllPermissionHandled()
        }

        viewModel.wallpaperPickRequest.observe(this) { request ->
            if (request == WallpaperRequest.STATIC_WALLPAPER) resultObserver.pickImage()
        }
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
    }

    override fun onBackPressed() {
        if (viewModel.wallpaperPickRequest.value == WallpaperRequest.LIVE_WALLPAPER) {
            viewModel.onDismissLiveWallpaperPicker()
        } else {
            finish()
        }
    }
}