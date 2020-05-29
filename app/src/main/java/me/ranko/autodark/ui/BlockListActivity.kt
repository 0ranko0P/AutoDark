package me.ranko.autodark.ui

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.transition.Fade
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.databinding.ActivityBlockListBinding

class BlockListActivity : BaseListActivity() {

    private lateinit var binding: ActivityBlockListBinding
    private lateinit var viewModel: BlockListViewModel
    private lateinit var mAdapter: BlockListAdapter

    private var menu: Menu? = null

    private val appListObserver = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            @Suppress("UNCHECKED_CAST")
            val list = (sender as ObservableField<List<ApplicationInfo>>).get()
            mAdapter.setData(list!!)
        }
    }

    private val statusObserver = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            val status = (sender as ObservableInt).get()
            if (status == Constant.JOB_STATUS_SUCCEED)
                showMessage(R.string.app_upload_success)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        with(window) {
            requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            enterTransition = Fade()
        }

        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, BlockListViewModel.Companion.Factory(application))
            .get(BlockListViewModel::class.java)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_block_list)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        ViewUtil.setAppBarPadding(binding.appBar)

        mAdapter = BlockListAdapter(viewModel)
        binding.recyclerView.adapter = mAdapter

        viewModel.attachViewModel(binding.toolbarEdit)
        viewModel.uploadStatus.addOnPropertyChangedCallback(statusObserver)

        viewModel.isSearching.observe(this, Observer { isSearching ->
            // hide menu icon while searching
            menu?.findItem(R.id.action_save)?.isVisible = !isSearching
            if (!isSearching) binding.toolbarEdit.text?.clear()
            mAdapter.setSearchMode(isSearching)
        })

        viewModel.isRefreshing.observe(this, Observer { isRefreshing ->
            if (!isRefreshing && binding.swipeRefresh.visibility == View.INVISIBLE && !viewModel.isUploading()) {
                // on first init
                binding.progressRoot.visibility = View.INVISIBLE
                binding.swipeRefresh.visibility = View.VISIBLE
                binding.progressText.setText(R.string.app_upload_start)
            } else {
                binding.swipeRefresh.isRefreshing = isRefreshing
            }
            binding.toolbarEdit.visibility = if (isRefreshing) View.INVISIBLE else View.VISIBLE
        })

        viewModel.appList.addOnPropertyChangedCallback(appListObserver)

        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshList() }
        binding.swipeRefresh.setColorSchemeResources( // add RGB power
            R.color.material_red_A700,
            R.color.material_green_A700,
            R.color.material_blue_A700
        )
    }

    private fun showMessage(@StringRes str: Int) =
        Snackbar.make(binding.coordinatorRoot, str, Snackbar.LENGTH_SHORT).show()

    override fun onBackPressed() {
        if (viewModel.isUploading()) {
            // prevent exit while uploading
            showMessage(R.string.app_upload_start)
        } else {
            if (binding.toolbarEdit.hasFocus()) {
                binding.toolbarEdit.clearFocus()
            } else {
                super.onBackPressed()
            }
        }
    }

    fun onRequestSave(v: View?) {
        if (binding.swipeRefresh.isRefreshing || !viewModel.requestUploadList()) showMessage(R.string.app_upload_busy)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_block_list, menu)
        this.menu = menu
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> onRequestSave(null)

            android.R.id.home -> onBackPressed()
        }
        return true
    }

    override fun onNavBarHeightAvailable(height: Int) {
        binding.recyclerView.apply {
            setPadding(paddingLeft, paddingTop + ViewUtil.getStatusBarHeight(resources) + 24, paddingRight, paddingBottom + height)
        }
        binding.swipeRefresh.setProgressViewOffset(false, 0, binding.recyclerView.paddingTop + 32)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.appList.removeOnPropertyChangedCallback(appListObserver)
        viewModel.uploadStatus.removeOnPropertyChangedCallback(statusObserver)
        viewModel.detach()
    }
}