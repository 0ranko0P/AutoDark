package me.ranko.autodark.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.content_permission_scroll.view.*
import me.ranko.autodark.R
import me.ranko.autodark.Utils.CircularAnimationUtil
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuApi.REQUEST_CODE_SHIZUKU_PERMISSION
import me.ranko.autodark.core.ShizukuStatus
import me.ranko.autodark.databinding.PermissionActivityBinding
import me.ranko.autodark.ui.widget.PermissionLayout
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import timber.log.Timber

class PermissionActivity : BaseListActivity(), ViewTreeObserver.OnGlobalLayoutListener {
    private lateinit var binding: PermissionActivityBinding

    /**
     * Coordinates that circle animate starts
     * **Nullable** if Activity started without Animation
     *
     * @see     PermissionActivity.startWithAnimationForResult
     * */
    private var coordinate: IntArray? = null

    private var shizukuDialog: AlertDialog? = null

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            viewModel.grantWithShizuku()
        } else {
            Snackbar.make(binding.coordRoot, R.string.permission_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

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
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.permission_activity)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        initShizukuCard()
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        ViewUtil.setAppBarPadding(binding.appbarPermission)

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
    }

    override fun getListView(): View = binding.content.permissionRoot

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
            shizukuListener.onRequestPermissionResult(REQUEST_CODE_SHIZUKU_PERMISSION, grantResults[0])
        }
    }

    /**
     * Called on grant with Shizuku button clicked
     * */
    fun onShizukuClick(v: View?) {
        when (ShizukuApi.checkShizuku(this)) {

            ShizukuStatus.DEAD -> showShizukuDeadDialog()

            ShizukuStatus.NOT_INSTALL -> {
                Snackbar.make(binding.coordRoot, R.string.shizuku_not_install, Snackbar.LENGTH_SHORT).show()
            }

            ShizukuStatus.UNAUTHORIZED -> ShizukuApi.requestPermission(this)

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
        val shizukuInstalled = ShizukuProvider.isShizukuInstalled(this)
        val viewStub = (if(shizukuInstalled) binding.coordRoot.stubShizukuFirst else binding.coordRoot.stubShizukuLast)
        val view = viewStub.inflate()
        if (shizukuInstalled) {
            val rotate = AnimationUtils.loadAnimation(view.context, R.anim.rotate_infinite)
            (view as PermissionLayout).titleIcon.startAnimation(rotate)
        }
    }

    private fun showRootView() {
        binding.coordRoot.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        shizukuDialog?.dismiss()
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    companion object {
        private const val ARG_COORDINATE: String = "ARG_COORDINATE"

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