package me.ranko.autodark.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import com.android.wallpaper.picker.AppbarFragment
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.R
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuStatus
import me.ranko.autodark.databinding.FragmentDarkWallpaperPermissionBinding
import me.ranko.autodark.ui.widget.PermissionLayout

/**
 * Fragment to request wallpaper permissions
 * */
class DarkWallpaperPermissionFragment : AppbarFragment() {

    private var oldStatusBarColor = 0

    private lateinit var viewModel: DarkWallpaperPickerViewModel

    private lateinit var mBinding: FragmentDarkWallpaperPermissionBinding

    private lateinit var mListener: PermissionListener

    interface PermissionListener {
        fun onAllPermissionHandled()
    }

    companion object {
        private const val REQUEST_PERMISSION_STORAGE = 615
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = requireActivity() as PermissionListener
        viewModel = ViewModelProvider(requireActivity(),
            DarkWallpaperPickerViewModel.Companion.Factory(requireActivity().application)).get()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mBinding = FragmentDarkWallpaperPermissionBinding.inflate(inflater, container, false)
        mBinding.viewModel = viewModel
        mBinding.lifecycleOwner = viewLifecycleOwner
        return mBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val status = viewModel.status.value
        if (status == ShizukuStatus.AVAILABLE) {
            mBinding.shizukuLayout.visibility = View.GONE
        } else {
            if (status == ShizukuStatus.UNAUTHORIZED || ShizukuApi.isShizukuInstalled(requireContext())) {
                val rotate = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate_infinite)
                mBinding.shizukuLayout.titleIcon.startAnimation(rotate)
            }
            val icon = ContextCompat.getDrawable(view.context, R.drawable.ic_close_gm2_24px)!!
            icon.setTint(view.context.getColor(R.color.primary))
            mBinding.shizukuHide.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            mBinding.shizukuHide.setOnClickListener(this::onShizukuClick)
            mBinding.shizukuRequest.setOnClickListener(this::onShizukuClick)
        }
        mBinding.storageRequest.setOnClickListener(this::onStorageClick)

        viewModel.shizukuRequesting.observe(viewLifecycleOwner, { requested ->
            if (requested == null || requested == true) return@observe // skip requesting

            if (viewModel.isShizukuAvailable()) {
                hidePermissionCard(mBinding.shizukuLayout)
            } else {
                onPermissionDenied()
            }
        })

        setHasOptionsMenu(true);
        // Included layout, remove unnecessary views in toolbar
        val toolbar = mBinding.root.findViewById<Toolbar>(R.id.toolbar)
        toolbar.removeAllViews()
        setUpToolbar(toolbar)

        val activity = requireActivity() as DarkWallpaperPickerActivity
        activity.setSupportActionBar(toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        adjustStatusBarColor(false)
    }

    private fun onPermissionDenied() {
        Snackbar.make(mBinding.root, R.string.permission_failed, Snackbar.LENGTH_SHORT).show()
    }

    private fun onShizukuClick(v: View) {
        if (v.id == mBinding.shizukuRequest.id) {
            when (viewModel.status.value) {
                ShizukuStatus.AVAILABLE -> hidePermissionCard(mBinding.shizukuLayout)

                ShizukuStatus.DEAD -> ShizukuApi.buildShizukuDeadDialog(requireActivity()).show()

                ShizukuStatus.UNAUTHORIZED -> viewModel.requestPermission(this)

                else -> Snackbar.make(mBinding.root, R.string.shizuku_not_install, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            hidePermissionCard(mBinding.shizukuLayout)
        }
    }

    private fun onStorageClick(@Suppress("UNUSED_PARAMETER")v: View?) {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showManualInstruction()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", requireContext().packageName, null)
            intent.data = uri
            startActivityForResult(intent, REQUEST_PERMISSION_STORAGE)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_PERMISSION_STORAGE
            )
        }
    }

    /**
     * Hide permission description card, if all card are hidden means
     * permissions are handled, notify [PermissionListener] at this time.
     * */
    private fun hidePermissionCard(targetCard: PermissionLayout) {
        targetCard.animate()
            .alpha(0f)
            .setDuration(resources.getInteger(android.R.integer.config_mediumAnimTime).toLong())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    animation?.removeListener(this)
                    targetCard.visibility = View.GONE
                    if (mBinding.shizukuLayout.isGone && mBinding.storageLayout.isGone) {
                        adjustStatusBarColor(true)
                        mListener.onAllPermissionHandled()
                    }
                }
            })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSION_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    hidePermissionCard(mBinding.storageLayout)
                } else {
                    showManualInstruction()
                    onPermissionDenied()
                }
            }

            ShizukuApi.REQUEST_CODE_SHIZUKU_PERMISSION -> {
                viewModel.onRequestPermissionResultPre11(requestCode, grantResults[0])
            }

            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PERMISSION_STORAGE) {
            if (DarkWallpaperPickerActivity.storageGranted(requireContext())) {
                hidePermissionCard(mBinding.storageLayout)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun showManualInstruction() {
        if (mBinding.storageLayout.getTag(R.id.appID) != null) return

        val newButtonText = getString(R.string.permission_wallpaper_manually_title)
        mBinding.storageRequest.text = newButtonText
        mBinding.storageLayout.description = getString(
            R.string.permission_wallpaper_manually,
            mBinding.storageLayout.description,
            newButtonText
        )
        // Mark Instruction updated
        mBinding.storageLayout.setTag(R.id.appID, true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            requireActivity().finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun getDefaultTitle(): CharSequence = getString(R.string.pref_dark_wallpaper_title)

    /**
     * Change status bar color for permission view
     * */
    private fun adjustStatusBarColor(restore: Boolean) {
        with(requireActivity()) {
            window.statusBarColor = if (restore) {
                oldStatusBarColor
            } else {
                oldStatusBarColor = window.statusBarColor
                getColor(R.color.bottom_sheet_background)
            }
            // adjust navBar too
            window.navigationBarColor = window.statusBarColor
        }
    }
}