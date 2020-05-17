package me.ranko.autodark.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.databinding.MainActivityBinding

class MainActivity : BaseListActivity(), FragmentManager.OnBackStackChangedListener {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainActivityBinding

    private var restrictedDialog: BottomSheetDialog? = null

    private val summaryTextListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            val summary = (sender as ObservableField<*>).get()
            showSummary(summary as MainViewModel.Companion.Summary)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this
        viewModel = ViewModelProvider(this, MainViewModel.Companion.Factory(application))
                .get(MainViewModel::class.java)
        binding.viewModel = viewModel

        viewModel.summaryText.addOnPropertyChangedCallback(summaryTextListener)
        viewModel.requirePermission.observe(this, Observer { required ->
            if (!required) return@Observer // ignore consumed signal
            // Show permission UI now
            PermissionActivity.startWithAnimationForResult(binding.fab, this)
            viewModel.onRequirePermissionConsumed()
        })

        if (ViewUtil.isLandscape(this)) {
            val collapsingToolbar =
                    binding.appbar.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)!!
            val transparent = ColorStateList.valueOf(getColor(android.R.color.transparent))
            collapsingToolbar.setExpandedTitleTextColor(transparent)
        } else {
            ViewUtil.setImmersiveNavBar(window)
        }

        supportFragmentManager.addOnBackStackChangedListener(this)

        if (savedInstanceState == null) {
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.container, MainFragment())
            transaction.commit()
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
        Snackbar.make(binding.coordinatorRoot, summary.message, Snackbar.LENGTH_LONG)
                .setAction(summary.actionStr, summary.action)
                .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PermissionActivity.REQUEST_CODE_PERMISSION) {
            if (resultCode == Activity.RESULT_OK)
                showSummary(MainViewModel.Companion.Summary(getString(R.string.permission_granted)))
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackStackChanged() {
        val frag = supportFragmentManager.findFragmentById(R.id.container) as PreferenceFragmentCompat
        frag.listView.apply {
            setPadding(paddingLeft, paddingTop, paddingRight, getNavBarHeight())
            if (clipToPadding) clipToPadding = false
        }
    }

    override fun onNavBarHeightAvailable(height: Int) {
        onBackStackChanged()
    }

    override fun onStop() {
        restrictedDialog?.run { if (isShowing) dismiss() }
        super.onStop()
    }

    override fun onDestroy() {
        viewModel.summaryText.removeOnPropertyChangedCallback(summaryTextListener)
        supportFragmentManager.removeOnBackStackChangedListener(this)
        super.onDestroy()
    }
}