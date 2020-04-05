package me.ranko.autodark.ui

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.content_permission_scroll.view.*
import kotlinx.coroutines.launch
import me.ranko.autodark.R
import me.ranko.autodark.Utils.CircularAnimationUtil
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuStatus
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

    private var shizukuDialog: AlertDialog? = null

    private val viewModel: PermissionViewModel by lazy {
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
        if (!ViewUtil.isLandscape(this)) ViewUtil.setImmersiveNavBar(window)

        binding = DataBindingUtil.setContentView(this, R.layout.permission_activity)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        initShizukuCard()

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onShizukuClick(null)
            } else {
                // denied
                Snackbar.make(binding.coordRoot, R.string.permission_failed, Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Called on grant with Shizuku button clicked
     * */
    fun onShizukuClick(v: View?) { lifecycleScope.launch {
        when (ShizukuApi.checkShizuku(this@PermissionActivity)) {

            ShizukuStatus.DEAD -> showShizukuDeadDialog()

            ShizukuStatus.NOT_INSTALL -> {
                Snackbar.make(binding.coordRoot, R.string.shizuku_not_install, Snackbar.LENGTH_SHORT).show()
            }

            ShizukuStatus.UNAUTHORIZED -> {
                requestPermissions(arrayOf(ShizukuApiConstants.PERMISSION), REQUEST_CODE_SHIZUKU_PERMISSION)
            }

            ShizukuStatus.AVAILABLE -> viewModel.grantWithShizuku()
        }}
    }

    private fun showShizukuDeadDialog() {
        val onDialogClick = DialogInterface.OnClickListener { dialog, which ->
            dialog.dismiss()
            if (which == DialogInterface.BUTTON_NEUTRAL)
                ShizukuApi.startManagerActivity(this@PermissionActivity)
        }

        shizukuDialog = AlertDialog.Builder(this, R.style.SimpleDialogStyle)
            .setView(android.R.layout.simple_list_item_1)
            .setNeutralButton(R.string.shizuku_open_manager, onDialogClick)
            .setPositiveButton(android.R.string.cancel, onDialogClick)
            .show()
        shizukuDialog?.findViewById<TextView>(android.R.id.text1)!!.apply {
            val padding = resources.getDimensionPixelOffset(R.dimen.permission_padding_description_horizontal)
            setText(R.string.shizuku_connect_failed)
            setPadding(padding, padding, padding, padding)
        }
    }

    override fun onGlobalLayout() {
        binding.coordRoot.viewTreeObserver.removeOnGlobalLayoutListener(this)
        val animator = CircularAnimationUtil.buildAnimator(coordinate!!, binding.coordRoot)
        showRootView()
        animator.duration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        animator.start()
    }

    private fun initShizukuCard() {
        val shizukuInstalled = ShizukuClientHelper.isManagerV3Installed(this)
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
        super.onDestroy()
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