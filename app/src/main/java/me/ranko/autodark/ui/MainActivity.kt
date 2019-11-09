package me.ranko.autodark.ui

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.databinding.MainActivityBinding

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainActivityBinding

    private val summaryTextListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            val text = (sender as ObservableField<*>).get().toString()

            Snackbar.make(binding.coordinatorRoot, text, Snackbar.LENGTH_LONG)
                .setAction(R.string.dark_mode_summary_action) { viewModel.setDarkModeManually() }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProviders.of(this, MainViewModel.Companion.Factory(application))
            .get(MainViewModel::class.java)

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        viewModel.summaryText.addOnPropertyChangedCallback(summaryTextListener)

        viewModel.requireAdb.observe(this, Observer { required ->
            if (required) {
                PermissionActivity.startWithAnimation(binding.fab,this)
                viewModel.onRequireAdbConsumed()
            }
        })

        if (ViewUtil.isLandscape(window)) {
            val collapsingToolbar = binding.appbar.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)!!
            val transparent = ColorStateList.valueOf(getColor(android.R.color.transparent))
            collapsingToolbar.setExpandedTitleTextColor(transparent)
        } else {
            ViewUtil.setImmersiveNavBar(window)
        }
    }

    override fun onDestroy() {
        viewModel.summaryText.removeOnPropertyChangedCallback(summaryTextListener)

        super.onDestroy()
    }
}