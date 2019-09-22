package me.ranko.autodark.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.Constant.JOB_STATUS_FAILED
import me.ranko.autodark.R
import me.ranko.autodark.databinding.MainActivityBinding
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainActivityBinding

    private var dialog: Dialog? = null

    /**
     * Show failed toast message
     * */
    private val suJobListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            if ((sender as ObservableInt).get() == JOB_STATUS_FAILED)
                Toast.makeText(application, R.string.root_check_failed, Toast.LENGTH_SHORT).show()
        }
    }

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

        viewModel.sudoJobStatus.addOnPropertyChangedCallback(suJobListener)
        viewModel.summaryText.addOnPropertyChangedCallback(summaryTextListener)

        viewModel.requireAdb.observe(this, Observer { required ->
            if (required) {
                PermissionActivity.startPermissionActivity(this)
                viewModel.onRequireAdbConsumed()
            }
        })
    }

    override fun onDestroy() {
        viewModel.sudoJobStatus.removeOnPropertyChangedCallback(suJobListener)
        viewModel.summaryText.removeOnPropertyChangedCallback(summaryTextListener)

        dialog?.run {
            if (isShowing) dismiss()
        }
        super.onDestroy()
    }
}