package me.ranko.autodark.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_block_list.*
import kotlinx.coroutines.launch
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil

class BlockListActivity : BaseListActivity() {

    private lateinit var viewModel: BlockListViewModel

    private lateinit var mAdapter: BlockListAdapter

    private var mDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_list)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        fab.setOnClickListener { onRequestSave() }

        ViewUtil.setAppBarPadding(app_bar)
        if (!ViewUtil.isLandscape(this)) ViewUtil.setImmersiveNavBar(window)

        viewModel = ViewModelProvider(this, BlockListViewModel.Companion.Factory(application))
                .get(BlockListViewModel::class.java)
        viewModel.register()
        viewModel.uploadStatus.observe(this, Observer { status ->
            if (status == null) return@Observer
            when (status) {
                Constant.JOB_STATUS_PENDING -> showLoadingDialog()

                Constant.JOB_STATUS_SUCCEED -> showMessage(R.string.app_upload_success)

                Constant.JOB_STATUS_FAILED -> showMessage(R.string.app_upload_fail)
            }
            if (status != Constant.JOB_STATUS_PENDING) mDialog?.dismiss()
        })

        swipeRefresh.setOnRefreshListener { refresh() }
        // add RGB power
        swipeRefresh.setColorSchemeResources(R.color.material_red_A700, R.color.material_green_A700, R.color.material_blue_A700)

        mAdapter = BlockListAdapter(viewModel)
        recyclerView.adapter = mAdapter
        refresh()
    }

    private fun refresh() = lifecycleScope.launch {
        if (!viewModel.isUploading()) {
            recyclerView.visibility = View.INVISIBLE
            val appList = viewModel.reloadListAsync().await()
            mAdapter.setData(appList)
        }
        recyclerView.visibility = View.VISIBLE
        swipeRefresh.isRefreshing = false
    }

    private fun showLoadingDialog() {
        if (mDialog == null) {
            mDialog = MaterialAlertDialogBuilder(this)
                    .setView(R.layout.dialog_loading)
                    .setCancelable(false)
                    .create()
        }
        mDialog!!.show()
    }

    private fun showMessage(@StringRes str: Int) = Snackbar.make(coordinatorRoot, str, Snackbar.LENGTH_SHORT).show()

    override fun onBackPressed() {
        if (viewModel.isUploading()) {
            showMessage(R.string.app_upload_busy)
        } else {
            super.onBackPressed()
        }
    }

    private fun onRequestSave() {
        if (viewModel.requestUploadList()) showMessage(R.string.app_upload_busy)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_block_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_save) {
            onRequestSave()
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    override fun onNavBarHeightAvailable(height: Int) {
        recyclerView.apply {
            setPadding(paddingLeft, paddingTop + ViewUtil.getStatusBarHeight(resources), paddingRight, paddingBottom + height)
        }
        swipeRefresh.setProgressViewOffset(false, 0, recyclerView.paddingTop + 32)
    }
}