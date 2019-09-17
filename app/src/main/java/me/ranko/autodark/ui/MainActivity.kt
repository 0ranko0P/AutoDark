package me.ranko.autodark.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel

    private var dialog: Dialog? = null
    private var btnRoot: TextView? = null
    private var processRoot: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        val root = findViewById<CoordinatorLayout>(R.id.coordinatorRoot)

        viewModel = ViewModelProviders.of(this, MainViewModel.Companion.Factory(application))
            .get(MainViewModel::class.java)

        viewModel.masterSwitch.observe(this, Observer<Boolean> { status ->
            val icon = if (status) R.drawable.ic_on else R.drawable.ic_off
            fab.setImageDrawable(getDrawable(icon))
        })

        viewModel.summaryText.observe(this, Observer<String> { t ->
            if(t == null) return@Observer

            Snackbar.make(root, t, Snackbar.LENGTH_LONG)
                .setAction(R.string.dark_mode_summary_action) { viewModel.setDarkModeManually() }
                .show()
            viewModel.setSummaryConsumed()
        })

        viewModel.requireAdb.observe(this, Observer { required ->
            if(required == null) return@Observer

            if (required) {
                showPermissionDialog()
            } else {
                dialog?.dismiss()
                viewModel.setAdbConsumed()
            }
        })

        viewModel.sudoJobStatus.observe(this, Observer { status ->
            if(status == -1) return@Observer

            when (status) {
                JOB_STATUS_PENDING -> showRootJobProgress(true)

                JOB_STATUS_FAILED -> {
                    showRootJobProgress(false)
                    showToast(R.string.root_check_failed)
                }

                JOB_STATUS_SUCCEED -> {
                    dialog?.dismiss()
                    viewModel.setRootConsumed()
                }
            }
        })
    }

    fun triggerMasterSwitch(v: View) {
        viewModel.triggerMasterSwitch()
    }

    /**
     * Show a full screen dialog ask user for permission
     * */
    // todo dataBinding: move set listener logic to xml
    private fun showPermissionDialog() {
        if (dialog == null) {
            dialog = Dialog(this, R.style.DialogFullscreen).let {
                it.setContentView(R.layout.fragment_permission)
                it.setCancelable(true)
                it.findViewById<TextView>(R.id.infoAdb).movementMethod =
                    LinkMovementMethod.getInstance()

                it.findViewById<TextView>(R.id.btnSend).setOnClickListener { shareAdbCommand() }
                it.findViewById<TextView>(R.id.btnCopy).setOnClickListener { copyAdbCommand() }
                it.findViewById<TextView>(R.id.btnAdb).setOnClickListener { onAdbCheck() }

                processRoot = it.findViewById(R.id.progressRoot)
                btnRoot = it.findViewById<TextView>(R.id.btnRoot)
                btnRoot!!.setOnClickListener { viewModel.grantWithRoot() }
                it
            }
        }
        if (!dialog!!.isShowing) {
            dialog!!.show()
        }
    }

    /**
     * show progress bar on or off when execute root job
     * */
    private fun showRootJobProgress(pending: Boolean) {
        btnRoot?.visibility = if (pending) View.GONE else View.VISIBLE
        processRoot?.visibility = if (pending) View.VISIBLE else View.GONE
    }

    private fun onAdbCheck(){
        if (viewModel.checkPermissionGranted()) {
            viewModel.setAdbConsumed()
            dialog?.dismiss()
            Timber.v("Access granted through ADB.")
        } else {
            showToast(R.string.adb_check_failed)
        }
    }

    private fun copyAdbCommand() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData = ClipData.newPlainText("command", COMMAND_GRANT_ADB)
        clipboard.setPrimaryClip(clip)
        showToast(R.string.app_copy_adb)
    }

    private fun shareAdbCommand() {
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Null")
        sharingIntent.putExtra(Intent.EXTRA_TEXT, COMMAND_GRANT_ADB)
        startActivity(
            Intent.createChooser(sharingIntent, getString(R.string.adb_share_text))
        )
    }

    private fun showToast(resInt: Int) {
        Toast.makeText(this, resInt, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        dialog?.run {
            if (isShowing) dismiss()
        }

        super.onDestroy()
    }
}