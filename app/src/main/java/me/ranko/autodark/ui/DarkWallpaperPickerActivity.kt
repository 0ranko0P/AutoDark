package me.ranko.autodark.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewStub
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import com.android.wallpaper.picker.BasePreviewActivity
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.R
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuStatus
import me.ranko.autodark.model.CroppedWallpaperInfo
import me.ranko.autodark.ui.DarkWallpaperPickerViewModel.WallpaperRequest
import me.ranko.autodark.ui.StandalonePreviewActivity.Companion.ARG_WALLPAPER
import me.ranko.autodark.ui.widget.PermissionLayout
import rikka.shizuku.Shizuku
import timber.log.Timber

class DarkWallpaperPickerActivity : BasePreviewActivity() {

    companion object {
        private const val REQUEST_PERMISSION_STORAGE = 615
        private const val REQUEST_PICK_IMAGE = 233

        private fun storageGranted(context: Context): Boolean {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED
        }
    }

    private lateinit var viewModel: DarkWallpaperPickerViewModel
    private lateinit var container: FrameLayout

    private var permissionRoot: LinearLayout? = null
    private var storagePermission: PermissionLayout? = null
    private var shizukuPermission: PermissionLayout? = null
    private var shizukuProgressBar: ProgressBar? = null

    private var shizukuListener: Shizuku.OnRequestPermissionResultListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, DarkWallpaperPickerViewModel.Companion.Factory(application)).get()
        setContentView(R.layout.activity_dark_wallpaper_picker)
        container = findViewById(R.id.fragment_container)

        if (storageGranted(this)) {
            initPickerFragment()
        } else {
            initPermissionLayout()
        }

        viewModel.wallpaperPickRequest.observe(this, Observer<WallpaperRequest> { request ->
            if (request == WallpaperRequest.STATIC_WALLPAPER) {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_PICK_IMAGE)
            }
        })
    }

    private fun initPickerFragment() {
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, DarkWallpaperFragment())
                .commit()
        }
    }

    private fun initPermissionLayout() {
        val root = findViewById<ViewStub>(R.id.permissionStub).inflate()
        storagePermission = root.findViewById(R.id.permissionStorage)
        shizukuPermission = root.findViewById(R.id.permissionShizuku)
        shizukuProgressBar = shizukuPermission!!.findViewById(R.id.progressShizuku)
        permissionRoot = root as LinearLayout
        initShizukuPermissionCard(shizukuPermission!!)
    }

    /**
     * Included layout, do some initialization work here
     * */
    private fun initShizukuPermissionCard(container: PermissionLayout) {
        if (ShizukuApi.checkShizuku(this) == ShizukuStatus.AVAILABLE) {
            container.visibility = View.GONE
            return
        } else {
            shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                shizukuProgressBar?.visibility = View.GONE
                if (grantResult == PERMISSION_GRANTED) {
                    hidePermissionCard(true)
                } else {
                    Snackbar.make(container, R.string.permission_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
            Shizuku.addRequestPermissionResultListener(shizukuListener!!)
        }
        container.setTitle(R.string.chooser_live_wallpaper_restricted_title)
        container.setDescription(R.string.chooser_live_wallpaper_restricted_description)
        val hideButton = TextView(ContextThemeWrapper(this, R.style.CardButton))
        hideButton.setText(R.string.chooser_live_wallpaper_restricted_hide)
        hideButton.setOnClickListener(this::onShizukuClick)
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_close_gm2_24px)!!
        icon.setTint(getColor(R.color.primary))
        hideButton.setCompoundDrawablesRelativeWithIntrinsicBounds(icon,null,null,null)
        container.addView(hideButton)
        container.findViewById<TextView>(R.id.btnShizuku).setOnClickListener(this::onShizukuClick)
    }

    fun onShizukuClick(v: View) {
        if (v.id == R.id.btnShizuku) { // grant button
            when (ShizukuApi.checkShizuku(this)) {

                ShizukuStatus.DEAD -> ShizukuApi.buildShizukuDeadDialog(this).show()

                ShizukuStatus.NOT_INSTALL -> {
                    Snackbar.make(container, R.string.shizuku_not_install, Snackbar.LENGTH_SHORT).show()
                }

                ShizukuStatus.UNAUTHORIZED -> {
                    shizukuProgressBar?.visibility = View.VISIBLE
                    ShizukuApi.requestPermission(this)
                }

                else -> hidePermissionCard(true)
            }
        } else { // dismiss button
            hidePermissionCard(true)
        }
    }

    /**
     * Hide permission description card, if both card are hidden means
     * all permission request are handled, call [initPickerFragment] at this time.
     * */
    private fun hidePermissionCard(isShizuku: Boolean) {
        val targetCard = if (isShizuku) shizukuPermission!! else storagePermission!!

        targetCard.animate()
            .alpha(0f)
            .setDuration(resources.getInteger(android.R.integer.config_mediumAnimTime).toLong())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    targetCard.visibility = View.GONE
                    if (shizukuPermission!!.visibility == View.GONE && storagePermission!!.visibility == View.GONE) {
                        initPickerFragment()
                    }
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

    fun requestPermission(v: View?) {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri;
            startActivityForResult(intent, REQUEST_PERMISSION_STORAGE)
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_PERMISSION_STORAGE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
                hidePermissionCard(false)
            } else {
                showManualText()
            }
        } else if (requestCode == ShizukuApi.REQUEST_CODE_SHIZUKU_PERMISSION) {
            shizukuListener!!.onRequestPermissionResult(requestCode, grantResults[0])
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_PICK_IMAGE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val noDestination = viewModel.isLiveWallpaperPicked()
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

            REQUEST_PERMISSION_STORAGE -> {
                if (storageGranted(this)) {
                    hidePermissionCard(false)
                }
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun showManualText() {
        val text = storagePermission!!.findViewById<TextView>(R.id.description)
        val button = storagePermission!!.findViewById<TextView>(R.id.btnStorage)
        button.text = getString(R.string.permission_wallpaper_manually_title)
        text.text = getString(R.string.permission_wallpaper_manually, text.text.toString(), button.text.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        shizukuListener?.let {
            Shizuku.removeRequestPermissionResultListener(it)
        }
    }
}