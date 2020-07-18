package me.ranko.autodark.ui

import android.os.Bundle
import android.transition.Fade
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableInt
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.databinding.ActivityBlockListBinding
import java.nio.file.Files

class BlockListActivity : BaseListActivity(), View.OnFocusChangeListener {

    private lateinit var binding: ActivityBlockListBinding
    private lateinit var viewModel: BlockListViewModel
    private lateinit var mAdapter: BlockListAdapter

    private var menu: Menu? = null

    private val statusObserver = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            when ((sender as ObservableInt).get()) {
                Constant.JOB_STATUS_PENDING -> binding.progressText.setText(R.string.app_upload_start)

                Constant.JOB_STATUS_SUCCEED -> showMessage(R.string.app_upload_success)

                Constant.JOB_STATUS_FAILED -> {binding.progressText.setText(R.string.app_upload_fail)}
            }
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
        if (!viewModel.isUploading()) binding.progressText.setText(R.string.app_loading)

        mAdapter = BlockListAdapter(viewModel)
        binding.recyclerView.adapter = mAdapter

        viewModel.mAppList.observe(this, Observer { list -> mAdapter.setData(list) })
        viewModel.attachViewModel()
        viewModel.uploadStatus.addOnPropertyChangedCallback(statusObserver)

        viewModel.isRefreshing.observe(this, Observer { isRefreshing ->
            if (viewModel.isUploading()) return@Observer

            binding.swipeRefresh.isRefreshing = isRefreshing
            binding.toolbarEdit.visibility = if (isRefreshing) View.INVISIBLE else View.VISIBLE
        })

        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshList() }
        binding.swipeRefresh.setColorSchemeResources( // add RGB power
            R.color.material_red_A700,
            R.color.material_green_A700,
            R.color.material_blue_A700
        )

        lifecycle.addObserver(viewModel.mSearchHelper)
    }

    private fun showMessage(@StringRes str: Int, @Duration duration: Int = Snackbar.LENGTH_SHORT) =
        Snackbar.make(binding.coordinatorRoot, str, duration).show()

    override fun onBackPressed() {
        if (viewModel.isUploading()) {
            // prevent exit while uploading
            showMessage(R.string.app_upload_start)
        } else {
            if (binding.toolbarEdit.hasFocus()) {
                menu?.findItem(R.id.action_save)?.isVisible = true
                mAdapter.setSearchMode(false)
                viewModel.mSearchHelper.stopSearch()
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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_hook_sys).isChecked = Files.exists(Constant.BLOCK_LIST_SYSTEM_APP_CONFIG_PATH)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> onRequestSave(null)

            R.id.action_hook_sys -> {
                if (item.isChecked.not()) {
                    showMessage(R.string.app_hook_system_restart, Snackbar.LENGTH_LONG)
                }
                viewModel.onSysAppClicked(item)
            }

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

    fun getSearchEditText() = binding.toolbarEdit

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        // hide menu icon while searching
        if (hasFocus) {
            menu?.findItem(R.id.action_save)?.isVisible = false
            mAdapter.setSearchMode(true)
            viewModel.mSearchHelper.startSearch()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.uploadStatus.removeOnPropertyChangedCallback(statusObserver)
    }
}