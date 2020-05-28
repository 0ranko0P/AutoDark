package me.ranko.autodark.ui

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.transition.Fade
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.annotation.StringRes
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_block_list.*
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil

class BlockListActivity : BaseListActivity() {

    private lateinit var viewModel: BlockListViewModel
    private var mAdapter: BlockListAdapter? = null

    private var menu: Menu? = null

    private val appListObserver = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            @Suppress("UNCHECKED_CAST")
            val list = (sender as ObservableField<List<ApplicationInfo>>).get()
            if (mAdapter == null) {
                mAdapter = BlockListAdapter(viewModel)
                recyclerView.adapter = mAdapter
            }
            mAdapter!!.setData(list!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        with(window) {
            requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            enterTransition = Fade()
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_list)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        fab.setOnClickListener { onRequestSave() }

        ViewUtil.setAppBarPadding(app_bar)

        viewModel = ViewModelProvider(this, BlockListViewModel.Companion.Factory(application))
                .get(BlockListViewModel::class.java)
        viewModel.attachViewModel(toolbarEdit)
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

        viewModel.isSearching.observe(this, Observer { isSearching ->
            // hide menu icon while searching
            menu?.findItem(R.id.action_save)?.isVisible = !isSearching
            if (!isSearching) toolbarEdit.text?.clear()
            mAdapter?.setSearchMode(isSearching)
        })

        viewModel.isRefreshing.observe(this, Observer { isRefreshing ->
            if (!isRefreshing && swipeRefresh.visibility == View.INVISIBLE) {
                // on first init
                updateLoadUI(false)
            } else {
                swipeRefresh.isRefreshing = isRefreshing
            }
            toolbarEdit.visibility = if(isRefreshing) View.INVISIBLE else View.VISIBLE
        })

        viewModel.appList.addOnPropertyChangedCallback(appListObserver)

        swipeRefresh.setOnRefreshListener { viewModel.refreshList() }
        // add RGB power
        swipeRefresh.setColorSchemeResources(R.color.material_red_A700, R.color.material_green_A700, R.color.material_blue_A700)
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
            // prevent exit while uploading
            showMessage(R.string.app_upload_start)
        } else {
            if (toolbarEdit.hasFocus()) {
                toolbarEdit.clearFocus()
            } else {
                super.onBackPressed()
            }
        }
    }

    private fun onRequestSave() {
        if (swipeRefresh.isRefreshing || !viewModel.requestUploadList()) showMessage(R.string.app_upload_busy)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_block_list, menu)
        this.menu = menu
        return super.onCreateOptionsMenu(menu)
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
            setPadding(paddingLeft, paddingTop + ViewUtil.getStatusBarHeight(resources) + 24, paddingRight, paddingBottom + height)
        }
        swipeRefresh.setProgressViewOffset(false, 0, recyclerView.paddingTop + 32)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.appList.removeOnPropertyChangedCallback(appListObserver)
    }
}