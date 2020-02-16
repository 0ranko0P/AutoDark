package me.ranko.autodark.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.databinding.MainActivityBinding

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainActivityBinding

    private var restrictedDialog: BottomSheetDialog? = null

    private val summaryTextListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            @Suppress("UNCHECKED_CAST")
            val summary = (sender as ObservableField<MainViewModel.Companion.Summary>).get()!!
            showSummary(summary)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this, MainViewModel.Companion.Factory(application))
            .get(MainViewModel::class.java)

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        viewModel.summaryText.addOnPropertyChangedCallback(summaryTextListener)

        viewModel.requirePermission.observe(this, Observer { required ->
            if (required) {
                PermissionActivity.startWithAnimationForResult(binding.fab, this)
                viewModel.onRequireAdbConsumed()
            }
        })

        if (ViewUtil.isLandscape(this)) {
            val collapsingToolbar =
                binding.appbar.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)!!
            val transparent = ColorStateList.valueOf(getColor(android.R.color.transparent))
            collapsingToolbar.setExpandedTitleTextColor(transparent)
        } else {
            ViewUtil.setImmersiveNavBar(window)
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        viewModel.getDelayedSummary()?.run {
            // delayed summary exists, show summary
            showSummary(this)
        }

        // check on resume
        // so user won't ignore the receiver problem
        restrictedDialog = viewModel.getRestrictedDialog(this)
        restrictedDialog?.show()
    }

    private fun showSummary(summary: MainViewModel.Companion.Summary) {
        val snack = Snackbar.make(binding.coordinatorRoot, summary.message, Snackbar.LENGTH_LONG)
        summary.actionStr?.let { snack.setAction(it, summary.action) }
        snack.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PermissionActivity.REQUEST_CODE_PERMISSION) {
            if (resultCode == Activity.RESULT_OK) {
                viewModel.updateForceDarkTitle()
                Snackbar.make(binding.coordinatorRoot, R.string.permission_granted, Snackbar.LENGTH_SHORT)
                    .show()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onStop() {
        restrictedDialog?.run { if (isShowing) dismiss() }
        super.onStop()
    }

    override fun onDestroy() {
        viewModel.summaryText.removeOnPropertyChangedCallback(summaryTextListener)

        super.onDestroy()
    }
}