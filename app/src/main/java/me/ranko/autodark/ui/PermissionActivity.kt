package me.ranko.autodark.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import me.ranko.autodark.R
import me.ranko.autodark.Utils.CircularAnimationUtil
import me.ranko.autodark.databinding.PermissionActivityBinding
import timber.log.Timber

class PermissionActivity : AppCompatActivity(), ViewTreeObserver.OnGlobalLayoutListener {
    private lateinit var binding: PermissionActivityBinding

    private val viewModel: PermissionViewModel by lazy {
        ViewModelProviders
            .of(this, PermissionViewModel.Companion.Factory(application))
            .get(PermissionViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                Toast.makeText(applicationContext, R.string.permission_failed, Toast.LENGTH_SHORT)
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