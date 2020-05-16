package me.ranko.autodark.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_block_list.*
import kotlinx.coroutines.launch
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil

class BlockListActivity : BaseListActivity() {

    private lateinit var viewModel: BlockListViewModel

    private lateinit var mAdapter: BlockListAdapter

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
                Constant.JOB_STATUS_PENDING -> updateLoadUI(true)

                Constant.JOB_STATUS_SUCCEED -> {
                    updateLoadUI(false)
                    showMessage(R.string.app_upload_success)
                }

                Constant.JOB_STATUS_FAILED -> {
                    updateLoadUI(true)
                    progressText.setText(R.string.app_upload_fail)
                }
            }
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
            val appList = viewModel.reloadListAsync().await()
            mAdapter.setData(appList)
        }
        // use loadUI's progressBar at first init
        if (swipeRefresh.visibility == View.INVISIBLE) updateLoadUI(false)
        swipeRefresh.isRefreshing = false
    }

    private fun updateLoadUI(isLoading: Boolean) {
        if (isLoading) {
            swipeRefresh.visibility = View.INVISIBLE
            progressRoot.visibility = View.VISIBLE
            progressText.setText(R.string.app_upload_start)
        } else {
            progressRoot.visibility = View.INVISIBLE
            swipeRefresh.visibility = View.VISIBLE
        }
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
        if (swipeRefresh.isRefreshing || !viewModel.requestUploadList()) showMessage(R.string.app_upload_busy)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_block_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> onRequestSave()

            android.R.id.home -> onBackPressed()
        }
        return true
    }

    override fun onNavBarHeightAvailable(height: Int) {
        recyclerView.apply {
            setPadding(paddingLeft, paddingTop + ViewUtil.getStatusBarHeight(resources), paddingRight, paddingBottom + height)
        }
        swipeRefresh.setProgressViewOffset(false, 0, recyclerView.paddingTop + 32)
    }
}