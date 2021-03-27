package me.ranko.autodark.ui

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.transition.Fade
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.core.LoadStatus
import me.ranko.autodark.databinding.ActivityBlockListBinding
import me.ranko.autodark.ui.MainViewModel.Companion.Summary
import java.nio.file.Files

class BlockListActivity : BaseListActivity() {

    companion object {
        private const val TAG_CURRENT_FRAGMENT = "current"
    }

    private lateinit var binding: ActivityBlockListBinding
    private lateinit var viewModel: BlockListViewModel
    private lateinit var mAdapter: BlockListAdapter

    private var menu: Menu? = null

    /**
     * Scroll listener version of HideBottomViewOnScrollBehavior to make SnackBar happy
     * */
    private val mScrollListener by lazy(LazyThreadSafetyMode.NONE) {
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0 && binding.fab.isShown) {
                    binding.fab.hide()
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    binding.fab.show()
                }
            }
        }
    }

    private val mMessageObserver by lazy(LazyThreadSafetyMode.NONE) {
        object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable, propertyId: Int) {
                val message = (sender as ObservableField<*>).get() ?: return
                showMessage((message as Summary).message)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        with(window) {
            requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            enterTransition = Fade()
        }

        viewModel = ViewModelProvider(this, BlockListViewModel.Companion.Factory(application))
            .get(BlockListViewModel::class.java)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_block_list)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mAdapter = BlockListAdapter(this, object : BlockListAdapter.AppSelectListener {
            override fun onAppSelected(app: ApplicationInfo): Boolean {
                return viewModel.onAppSelected(app)
            }

            override fun isAppSelected(app: ApplicationInfo): Boolean {
               return viewModel.isBlocked(app)
            }
        })

        binding.recyclerView.adapter = mAdapter
        binding.recyclerView.addOnScrollListener(mScrollListener)

        viewModel.mAppList.observe(this, { list -> mAdapter.setData(list) })

        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshList() }
        binding.swipeRefresh.setColorSchemeResources( // add RGB power
            R.color.material_red_A700,
            R.color.material_green_A700,
            R.color.material_blue_A700
        )

        viewModel.dialog.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable, propertyId: Int) {
                val dialog = (sender as ObservableField<*>).get() ?: return
                (dialog as DialogFragment).show(supportFragmentManager, TAG_CURRENT_FRAGMENT)
                viewModel.dialog.set(null)
            }
        })

        viewModel.attachSearchHelper(this, binding.toolbarEdit)
        viewModel.isSearching.observe(this, { searching ->
            mAdapter.setSearchMode(searching)
            // hide menu icon while searching
            setMenuVisible(searching.not())
        })

        // hide all the stuff when update failed
        viewModel.uploadStatus.observe(this, { status ->
            if (status == LoadStatus.SUCCEED) {
                binding.fab.show()
                setMenuVisible(true)
            } else {
                binding.fab.hide()
                setMenuVisible(false)
            }
        })

        viewModel.message.addOnPropertyChangedCallback(mMessageObserver)
        if (savedInstanceState != null) {
            val message = viewModel.message.get()?: return
            showMessage(message.message)
        }
    }

    private fun showMessage(message: String, @Duration duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.coordinatorRoot, message, duration).show()
        viewModel.message.set(null)
    }

    override fun onBackPressed() {
        if (viewModel.isUploading()) {
            // prevent exit while uploading
            showMessage(getString(R.string.app_upload_busy))
        } else {
            if (binding.toolbarEdit.hasFocus()) {
                binding.toolbar.clearFocus()
            } else {
                super.onBackPressed()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_block_list, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        this.menu = menu
        menu.findItem(R.id.action_hook_sys).isChecked = viewModel.shouldShowSystemApp()
        menu.findItem(R.id.action_hook_ime).isChecked = Files.exists(Constant.BLOCK_LIST_INPUT_METHOD_CONFIG_PATH)

        viewModel.isRefreshing.observe(this, { isRefreshing ->
            if (isRefreshing) {
                binding.toolbarEdit.visibility = View.INVISIBLE
                binding.fab.hide()
            } else {
                binding.toolbarEdit.visibility = View.VISIBLE
                binding.fab.show()
            }
            binding.swipeRefresh.isRefreshing = isRefreshing
            mAdapter.setRefreshing(isRefreshing)
            setMenuVisible(isRefreshing.not())
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> binding.fab.performClick()

            R.id.action_hook_sys -> viewModel.onShowSysAppSelected(item.isChecked.not())

            R.id.action_hook_ime -> viewModel.onHookImeSelected(item)

            android.R.id.home -> onBackPressed()

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        super.onApplyWindowInsets(v, insets)
        val endOffset = getListView().paddingTop + statusBarHeight
        binding.swipeRefresh.setProgressViewOffset(false, 0, endOffset)
        viewModel.refreshList()
        return WindowInsetsCompat.CONSUMED
    }

    override fun getRootView(): View = binding.coordinatorRoot

    override fun getListView(): View = binding.recyclerView

    override fun getAppbar(): View = binding.appBar

    override fun applyInsetsToListPadding(top: Int, bottom: Int) {
        super.applyInsetsToListPadding(top, bottom)

        val fabParams = binding.fab.layoutParams as CoordinatorLayout.LayoutParams
        fabParams.bottomMargin = fabParams.bottomMargin + bottom
        binding.fab.layoutParams = fabParams
    }

    private fun setMenuVisible(visible: Boolean) {
        menu?.children?.forEach { item ->
            if (item.isVisible.xor(visible)) item.isVisible = visible
        }
    }

    override fun onDestroy() {
        binding.recyclerView.removeOnScrollListener(mScrollListener)
        binding.recyclerView.adapter = null
        super.onDestroy()
    }
}