package me.ranko.autodark.ui

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.R
import me.ranko.autodark.Receivers.DarkModeAlarmReceiver
import me.ranko.autodark.Utils.ComponentUtil
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.databinding.MainActivityBinding
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainActivityBinding

    /**
     * Show this dialog if boot receiver is disabled
     *
     * @see     checkBootReceiver
     * */
    private var receiverDialog: AlertDialog? = null

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

        checkBootReceiver()
    }

    /**
     * Some optimize app or OEM performance boost function can disable boot receiver
     * Notify user if this happened
     *
     * */
    private fun checkBootReceiver() {
        if (!ComponentUtil.isEnabled(this, DarkModeAlarmReceiver::class.java)) {
            Timber.e("checkBootReceiver: receiver disabled!")
            if (receiverDialog == null) {
                receiverDialog = AlertDialog.Builder(this)
                    .setTitle(R.string.app_receiver_disabled_title)
                    .setMessage(R.string.app_receiver_disabled_msg)
                    .setPositiveButton(R.string.app_confirm) { _, _ -> finish() }
                    .create()
            }

            receiverDialog!!.show()
        } else {
            Timber.d("checkBootReceiver: receiver enabled")
        }
    }

    override fun onStop() {
        receiverDialog?.run { if (isShowing) dismiss() }
        super.onStop()
    }

    override fun onDestroy() {
        viewModel.summaryText.removeOnPropertyChangedCallback(summaryTextListener)

        super.onDestroy()
    }
}