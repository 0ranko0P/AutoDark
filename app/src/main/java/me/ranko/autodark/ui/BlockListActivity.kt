package me.ranko.autodark.ui

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.transition.Fade
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.annotation.StringRes
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.activity_block_list.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil

class BlockListActivity : BaseListActivity() {

    private lateinit var viewModel: BlockListViewModel
    private lateinit var mAdapter: BlockListAdapter
    private lateinit var mAppList: MutableList<ApplicationInfo>

    private var menu: Menu? = null
    private val saveMenu: MenuItem by lazy(LazyThreadSafetyMode.NONE) { menu!!.findItem(R.id.action_save) }

    private val mSearchHelper = object: TextWatcher, View.OnFocusChangeListener {
        private var queryJob: Job? = null
        private var lastInput: CharSequence = ""

        override fun onFocusChange(v: View?, hasFocus: Boolean) {
            saveMenu.isVisible = !hasFocus
            mAdapter.setSearchMode(hasFocus)
            // clear app list if searching
            if (hasFocus) {
                mAdapter.clear()
            } else {
                if (!hasFocus) (v as TextInputEditText).text?.clear()
                if (queryJob?.isActive == true) queryJob?.cancel()
                mAdapter.setData(mAppList)
            }
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (s.isEmpty()) {
                mAdapter.clear()
                return
            }

            if (lastInput.isNotEmpty() && isAppendChars(lastInput, s)) {
                queryAndShow(s, mAdapter.getData()!!)
            } else {
                queryAndShow(s, mAppList)
            }
            lastInput = s
        }

        private fun queryAndShow(str: CharSequence, list: List<ApplicationInfo>) {
            if (queryJob?.isActive == true) queryJob?.cancel()
            queryJob = lifecycleScope.launch {
                // clear current result
                val data = ArrayList<ApplicationInfo>()
                mAdapter.setData(data)

                for (it: ApplicationInfo in list) {
                    val isPkg = it.packageName.contains(str, true)
                    val isLabel = viewModel.getAppName(it).contains(str, true)
                    if (isPkg || isLabel) {
                        data.add(it)
                        mAdapter.notifyItemInserted(list.size - 1)
                    }
                }
            }
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun afterTextChanged(s: Editable?) {
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

        toolbarEdit.onFocusChangeListener = mSearchHelper
        toolbarEdit.addTextChangedListener(mSearchHelper)
    }

    private fun refresh() = lifecycleScope.launch {
        if (!viewModel.isUploading()) {
            mAppList = viewModel.reloadListAsync().await()
            mAdapter.setData(mAppList)
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
            setPadding(paddingLeft, paddingTop + ViewUtil.getStatusBarHeight(resources), paddingRight, paddingBottom + height)
        }
        swipeRefresh.setProgressViewOffset(false, 0, recyclerView.paddingTop + 32)
    }

    override fun onDestroy() {
        super.onDestroy()
        toolbarEdit.removeTextChangedListener(mSearchHelper)
    }

    companion object {
        private fun isAppendChars(old: CharSequence, new: CharSequence): Boolean {
            if (old.length >= new.length || new.isEmpty()) return false
            for ((index, sChar) in old.withIndex()) {
                if (new[index] != sChar) return false
            }
            return true
        }
    }
}