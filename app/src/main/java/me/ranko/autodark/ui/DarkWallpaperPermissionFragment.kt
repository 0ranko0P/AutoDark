package me.ranko.autodark.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
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

    /**
     * @see DarkWallpaperPickerViewModel.checkMigrate
     * */
    private var isMigrating = false

    companion object {
        private const val ARG_IS_MIGRATE = "arg_M"
        val TAG = DarkWallpaperPermissionFragment::class.simpleName

        fun instance(isMigrating: Boolean): DarkWallpaperPermissionFragment {
            return DarkWallpaperPermissionFragment().apply {
                val bundle = Bundle()
                bundle.putBoolean(ARG_IS_MIGRATE, isMigrating)
                arguments = bundle
            }
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(RequestPermission()) { result ->
        if (result) {
            hidePermissionCard(mBinding.storageLayout)
        } else {
            showManualInstruction()
            onPermissionDenied()
        }
    }

    private val permissionSettingsLauncher = registerForActivityResult(StartActivityForResult()) {
        if (DarkWallpaperPickerActivity.storageGranted(requireContext())) {
            hidePermissionCard(mBinding.storageLayout)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel = ViewModelProvider(requireActivity(),
            DarkWallpaperPickerViewModel.Companion.Factory(requireActivity().application)).get()
        isMigrating = requireArguments().getBoolean(ARG_IS_MIGRATE)
        viewModel.registerPermissionPre11(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)
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

        viewModel.shizukuRequesting.observe(viewLifecycleOwner) { requested ->
            if (requested == null || requested == true) return@observe // skip requesting

            if (viewModel.isShizukuAvailable()) {
                hidePermissionCard(mBinding.shizukuLayout)
            } else {
                onPermissionDenied()
            }
        }

        // Included layout, remove unnecessary views in toolbar
        val toolbar = mBinding.root.findViewById<Toolbar>(R.id.toolbar)
        toolbar.removeAllViews()
        setUpToolbar(toolbar)
        val activity = requireActivity() as AppCompatActivity

        // Do not setup actionbar in migration mode
        if (isMigrating.not()) {
            mBinding.storageRequest.setOnClickListener(this::onStorageClick)
            setHasOptionsMenu(true)
            activity.setSupportActionBar(toolbar)
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        } else {
            activity.setSupportActionBar(null)
            mBinding.storageLayout.visibility = View.GONE
        }
        adjustStatusBarColor(false)
    }

    private fun onPermissionDenied() {
        Snackbar.make(mBinding.root, R.string.permission_failed, Snackbar.LENGTH_SHORT).show()
    }

    private fun onShizukuClick(v: View) {
        if (v.id == mBinding.shizukuRequest.id) {
            when (viewModel.status.value) {
                ShizukuStatus.UNAUTHORIZED -> viewModel.requestPermission()

                ShizukuStatus.DEAD -> ShizukuApi.buildShizukuDeadDialog(requireActivity()).show()

                ShizukuStatus.AVAILABLE -> hidePermissionCard(mBinding.shizukuLayout)

                else -> Snackbar.make(mBinding.root, R.string.shizuku_not_install, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            hidePermissionCard(mBinding.shizukuLayout)
        }
    }

    private fun onStorageClick(v: View) {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showManualInstruction()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", v.context.packageName, null)
            intent.data = uri
            permissionSettingsLauncher.launch(intent)
        } else {
            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Hide permission description card, if all card are hidden means
     * permissions are handled, notify viewModel at this time.
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
                        (requireActivity() as AppCompatActivity).setSupportActionBar(null)
                        viewModel.onAllPermissionHandled()
                    }
                }
            })
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