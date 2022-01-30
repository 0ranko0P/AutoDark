package me.ranko.autodark.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.R
import me.ranko.autodark.Utils.CircularAnimationUtil
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuStatus
import me.ranko.autodark.databinding.ActivityPermissionBinding
import me.ranko.autodark.ui.widget.PermissionLayout

/**
 * Activity that shows an instruction for granting [Manifest.permission.WRITE_SECURE_SETTINGS].
 * */
class PermissionActivity : BaseListActivity(), ViewTreeObserver.OnGlobalLayoutListener {
    private lateinit var binding: ActivityPermissionBinding

    /**
     * Coordinates that circle animate starts
     * **Nullable** if Activity started without Animation
     *
     * @see     PermissionActivity.startWithAnimationForResult
     * */
    private var coordinate: IntArray? = null

    private var shizukuDialog: AlertDialog? = null

    private val viewModel: PermissionViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this, PermissionViewModel.Companion.Factory(application)).get(
            PermissionViewModel::class.java
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        coordinate = intent.getIntArrayExtra(ARG_COORDINATE)
        if ( coordinate != null) {
            // replace default transition
            overridePendingTransition(R.anim.do_not_move, R.anim.do_not_move)
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_permission)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        lifecycle.addObserver(viewModel)
        super.onCreate(savedInstanceState)
        viewModel.registerPermissionPre11(this)

        initShizukuCard()

        viewModel.permissionResult.observe(this) { result ->
            if (result) {
                finish()
            } else {
                Snackbar.make(binding.coordRoot, R.string.permission_failed, Snackbar.LENGTH_SHORT).show()
            }
        }

        if (savedInstanceState == null && coordinate != null) {
            val viewTreeObserver = binding.coordRoot.viewTreeObserver
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.addOnGlobalLayoutListener(this)
            }
        } else {
            showRootView()
        }
    }

    override fun getRootView(): View = binding.coordRoot

    override fun getListView(): View = binding.content.permissionRoot

    override fun getAppbar(): View = binding.appbarPermission

    /**
     * Called on grant with Shizuku button clicked
     * */
    fun onShizukuClick(@Suppress("UNUSED_PARAMETER")v: View?) {
        when (viewModel.status.value as ShizukuStatus) {

            ShizukuStatus.DEAD -> showShizukuDeadDialog()

            ShizukuStatus.NOT_INSTALL -> {
                Snackbar.make(binding.coordRoot, R.string.shizuku_not_install, Snackbar.LENGTH_SHORT).show()
            }

            ShizukuStatus.UNAUTHORIZED -> viewModel.requestPermission()

            ShizukuStatus.AVAILABLE -> viewModel.grantWithShizuku()
        }
    }

    private fun showShizukuDeadDialog() {
        if (shizukuDialog == null) {
            shizukuDialog = ShizukuApi.buildShizukuDeadDialog(this)
        }
        shizukuDialog?.show()
    }

    override fun onGlobalLayout() {
        binding.coordRoot.viewTreeObserver.removeOnGlobalLayoutListener(this)
        val animator = CircularAnimationUtil.buildAnimator(coordinate!!, binding.coordRoot)
        showRootView()
        animator.duration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        animator.start()
    }

    private fun initShizukuCard() {
        val installed = ShizukuApi.isShizukuInstalled(this)
        val viewStub = if (installed) binding.content.stubShizukuFirst else binding.content.stubShizukuLast
        with(viewStub.viewStub!!.inflate() as PermissionLayout) {
            if (AutoDarkApplication.isSui) {
                // Remove root permission when Sui is available
                (binding.content.getRoot() as ViewGroup).removeView(binding.content.root)
            }

            description = getString(R.string.shizuku_description, title)
            if (installed) {
                val rotate = AnimationUtils.loadAnimation(context, R.anim.rotate_infinite)
                titleIcon.startAnimation(rotate)
            }
        }
    }

    private fun showRootView() {
        binding.coordRoot.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        shizukuDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        private const val ARG_COORDINATE: String = "ARG_COORDINATE"

        /**
         * Launch this activity for requesting permission from user
         * */
        fun startWithAnimationForResult(startView: View, launcher: ActivityResultLauncher<Intent>) {
            val intent = Intent(startView.context, PermissionActivity::class.java)
            val coordinate = CircularAnimationUtil.getViewCenterCoordinate(startView)
            intent.putExtra(ARG_COORDINATE, coordinate)
            launcher.launch(intent)
        }
    }
}