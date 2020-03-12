package me.ranko.autodark.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.R
import me.ranko.autodark.Utils.CircularAnimationUtil
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.databinding.PermissionActivityBinding
import me.ranko.autodark.ui.widget.PermissionLayout
import moe.shizuku.api.ShizukuApiConstants
import moe.shizuku.api.ShizukuClientHelper
import timber.log.Timber

class PermissionActivity : AppCompatActivity(), ViewTreeObserver.OnGlobalLayoutListener {
    private lateinit var binding: PermissionActivityBinding

    /**
     * Coordinates that circle animate starts
     * **Nullable** if Activity started without Animation
     *
     * @see     PermissionActivity.startWithAnimationForResult
     * */
    private var coordinate: IntArray? = null

    private val viewModel: PermissionViewModel by lazy {
        ViewModelProvider(this, PermissionViewModel.Companion.Factory(application)).get(
            PermissionViewModel::class.java
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ViewUtil.isLandscape(this)) ViewUtil.setImmersiveNavBar(window)

        coordinate = intent.getIntArrayExtra(ARG_COORDINATE)
        if ( coordinate != null) {
            // replace default transition
            overridePendingTransition(R.anim.do_not_move, R.anim.do_not_move)
        }

        binding = DataBindingUtil.setContentView(this, R.layout.permission_activity)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        viewModel.permissionResult.observe(this, Observer<Boolean> { result ->
            Timber.v("Access ${if (result) "granted" else "denied"}.")
            if (result) {
                setResult(RESULT_OK)
                finish()
            } else {
                Snackbar.make(binding.coordRoot, R.string.permission_failed, Snackbar.LENGTH_SHORT)
                    .show()
            }
        })

        if (savedInstanceState == null && coordinate != null) {
            val viewTreeObserver = binding.coordRoot.viewTreeObserver
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.addOnGlobalLayoutListener(this)
            }
        } else {
            showRootView()
        }

        binding.content.btnShizuku.setOnClickListener {
            if (!ShizukuClientHelper.isManagerV3Installed(this)) {
                Snackbar.make(binding.coordRoot, R.string.shizuku_not_install, Snackbar.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            if (viewModel.checkShizukuPermission()) {
                viewModel.grantWithShizuku()
            } else {
                requestPermissions(arrayOf(ShizukuApiConstants.PERMISSION), REQUEST_CODE_SHIZUKU_PERMISSION)
            }
        }

        moveShizukuToTop()
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
        val animator = CircularAnimationUtil.buildAnimator(coordinate!!, binding.coordRoot)
        showRootView()
        animator.duration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        animator.doOnEnd {
            // Expand description to let user know it is expandable
            // first child is description card
            val permissionCard = (binding.content.getRoot() as ViewGroup).getChildAt(1)
            (permissionCard as PermissionLayout).onClick(null);
        }
        animator.start()
    }

    private fun moveShizukuToTop() {
        if (ShizukuClientHelper.isManagerV3Installed(this)) {
            val shizukuView = binding.content.shizuku
            (shizukuView.parent as ViewGroup).apply {
                removeView(shizukuView)
                addView(shizukuView, 1)
            }
            val rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_infinite)
            shizukuView.titleIcon.startAnimation(rotate)
        }
    }

    private fun showRootView() {
        binding.coordRoot.visibility = View.VISIBLE
    }

    companion object {
        private const val ARG_COORDINATE: String = "ARG_COORDINATE"

        private const val REQUEST_CODE_SHIZUKU_PERMISSION = 7

        const val REQUEST_CODE_PERMISSION = 2233

        /**
         * Launch this activity for requesting permission from user
         *
         * @return  RESULT_OK onActivityResult callback if user
         *          granted permission
         *
         * @see     Activity.onActivityResult
         * @see     REQUEST_CODE_PERMISSION
         * */
        fun startWithAnimationForResult(startView: View, activity: Activity) {
            val intent = Intent(activity, PermissionActivity::class.java)
            val coordinate = CircularAnimationUtil.getViewCenterCoordinate(startView)
            intent.putExtra(ARG_COORDINATE, coordinate)
            activity.startActivityForResult(intent, REQUEST_CODE_PERMISSION)
        }
    }
}