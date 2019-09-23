package me.ranko.autodark.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import me.ranko.autodark.R
import me.ranko.autodark.databinding.PermissionActivityBinding
import timber.log.Timber

class PermissionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: PermissionActivityBinding =
            DataBindingUtil.setContentView(this, R.layout.permission_activity)

        val viewModel = ViewModelProviders
            .of(this, PermissionViewModel.Companion.Factory(application))
            .get(PermissionViewModel::class.java)

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
    }

    companion object {
        fun startPermissionActivity(context: Context) {
            val intent = Intent(context, PermissionActivity::class.java)
            startActivity(context, intent, null)
        }
    }
}
