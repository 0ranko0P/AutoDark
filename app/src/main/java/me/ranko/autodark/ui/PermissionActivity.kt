package me.ranko.autodark.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.R
import me.ranko.autodark.Utils.CircularAnimationUtil
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.databinding.PermissionActivityBinding
import moe.shizuku.api.ShizukuApiConstants
import moe.shizuku.api.ShizukuClientHelper
import timber.log.Timber

class PermissionActivity : AppCompatActivity(), ViewTreeObserver.OnGlobalLayoutListener {
    private lateinit var binding: PermissionActivityBinding

    private val viewModel: PermissionViewModel by lazy {
        ViewModelProvider(this, PermissionViewModel.Companion.Factory(application)).get(
            PermissionViewModel::class.java
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ViewUtil.isLandscape(window)) ViewUtil.setImmersiveNavBar(window)

        // replace default transition
        overridePendingTransition(R.anim.do_not_move, R.anim.do_not_move)

        binding = DataBindingUtil.setContentView(this, R.layout.permission_activity)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        viewModel.permissionResult.observe(this, Observer<Boolean> { result ->
            Timber.v("Access ${if (result) "granted" else "denied"}.")
            if (result) {
                finish()
            } else {
                Snackbar.make(binding.coordRoot, R.string.permission_failed, Snackbar.LENGTH_SHORT)
                    .show()
            }
        })

        if (savedInstanceState == null) {
            val viewTreeObserver = binding.coordRoot.viewTreeObserver
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.addOnGlobalLayoutListener(this)
            }
        } else {
            showRootView()
        }

        binding.shizukuRoot.btnShizuku.setOnClickListener {
            if (!ShizukuClientHelper.isManagerV3Installed(this)) {
                Snackbar.make(binding.coordRoot, R.string.permission_failed, Snackbar.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            if (viewModel.checkShizukuPermission()) {
                viewModel.grantWithShizuku()
            } else {
                requestPermissions(arrayOf(ShizukuApiConstants.PERMISSION), REQUEST_CODE_SHIZUKU_PERMISSION)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                viewModel.grantWithShizuku()
            } else {
                // denied
                Snackbar.make(binding.coordRoot, R.string.permission_failed, Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onGlobalLayout() {
        binding.coordRoot.viewTreeObserver.removeOnGlobalLayoutListener(this)
        val coordinate = intent.getIntArrayExtra(ARG_COORDINATE)!!
        val animator = CircularAnimationUtil.buildAnimator(coordinate, binding.coordRoot)
        showRootView()
        animator.duration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        animator.start()
    }

    private fun showRootView() {
        binding.coordRoot.visibility = View.VISIBLE
    }

    companion object {
        private const val ARG_COORDINATE: String = "ARG_COORDINATE"

        private const val REQUEST_CODE_SHIZUKU_PERMISSION = 7

        fun start(context: Context) {
            val intent = Intent(context, PermissionActivity::class.java)
            context.startActivity(intent)
        }

        fun startWithAnimation(startView: View, activity: Activity) {
            val intent = Intent(activity, PermissionActivity::class.java)
            val coordinate = CircularAnimationUtil.getViewCenterCoordinate(startView)
            intent.putExtra(ARG_COORDINATE, coordinate)
            activity.startActivity(intent)
        }
    }
}