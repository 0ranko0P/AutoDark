package me.ranko.autodark.ui

import android.os.Bundle
import android.transition.Fade
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil
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

    private val mDialogObserver by lazy(LazyThreadSafetyMode.NONE) {
        object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable, propertyId: Int) {
                val dialog = (sender as ObservableField<*>).get() ?: return
                (dialog as DialogFragment).show(supportFragmentManager, TAG_CURRENT_FRAGMENT)
                viewModel.dialog.set(null)
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

        mAdapter = BlockListAdapter(this, Glide.with(this), viewModel)

        binding.recyclerView.adapter = mAdapter
        binding.recyclerView.addOnScrollListener(mScrollListener)

        viewModel.mAppList.observe(this, { list -> mAdapter.setData(list) })

        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshList() }
        binding.swipeRefresh.setColorSchemeResources( // add RGB power
            R.color.material_red_A700,
            R.color.material_green_A700,
            R.color.material_blue_A700
        )

        viewModel.isRefreshing.observe(this, { isRefreshing ->
            if (isRefreshing) {
                binding.fab.hide()
                binding.toolbarEdit.visibility = View.INVISIBLE
            } else {
                binding.fab.show()
                binding.toolbarEdit.visibility = View.VISIBLE
            }
            binding.swipeRefresh.isRefreshing = isRefreshing
            mAdapter.setRefreshing(isRefreshing)
            setMenuVisible(isRefreshing.not() && viewModel.isEditing().not())
        })

        viewModel.dialog.addOnPropertyChangedCallback(mDialogObserver)

        viewModel.attachSearchHelper(this, binding.toolbarEdit)
        viewModel.isSearching.observe(this, { searching ->
            mAdapter.setSearchMode(searching)
            // hide menu icon while searching
            setMenuVisible(searching.not())
        })

        viewModel.isEditing.observe(this, { editing ->
            val iconColor = if (editing) {
                binding.fab.setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_input_add))
                getColor(R.color.primary)
            } else {
                binding.fab.setImageResource(R.drawable.ic_save)
                ViewUtil.getAttrColor(this, R.attr.colorOnSurface)
            }
            // use primary icon color when editing
            menu?.findItem(R.id.action_edit)?.icon?.mutate()?.setTint(iconColor)
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

    override fun onBackPressed() = when {
        // prevent exit while uploading
        viewModel.isUploading() -> showMessage(getString(R.string.app_upload_busy))

        viewModel.isEditing() -> viewModel.onEditMode()

        binding.toolbarEdit.hasFocus() -> binding.toolbar.clearFocus()

        else -> super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_block_list, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        this.menu = menu
        menu.findItem(R.id.action_hook_sys).isChecked = viewModel.shouldShowSystemApp()
        menu.findItem(R.id.action_blocked_first).isChecked = viewModel.isBlockedFirst()
        menu.findItem(R.id.action_hook_ime).isChecked = Files.exists(Constant.BLOCK_LIST_INPUT_METHOD_CONFIG_PATH)

        val groupTitleColor = getColor(R.color.primary)
        ViewUtil.setMenuItemTitleColor(menu.findItem(R.id.group_list), groupTitleColor)
        ViewUtil.setMenuItemTitleColor(menu.findItem(R.id.group_xposed), groupTitleColor)
        setMenuVisible(viewModel.isRefreshAvailable() && viewModel.isEditing().not())
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> viewModel.onEditMode()

            R.id.action_save -> binding.fab.performClick()

            R.id.action_hook_sys -> viewModel.onShowSysAppSelected(item.isChecked.not())

            R.id.action_blocked_first -> viewModel.onBlockFirstSelected(item.isChecked.not())

            R.id.action_hook_ime -> viewModel.onHookImeSelected(item)

            android.R.id.home -> onBackPressed()

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        super.onApplyWindowInsets(v, insets)
        val actionBarSize = getListView().paddingTop
        val endOffset = actionBarSize + statusBarHeight
        binding.swipeRefresh.setProgressViewOffset(false, actionBarSize, endOffset)

        // avoid refresh app list while editing or searching
        if (viewModel.isEditing().not() && binding.toolbarEdit.hasFocus().not()) {
            viewModel.refreshList()
        }
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

    private fun setMenuVisible(visible: Boolean) = menu?.children?.forEach { item ->
        if (item.itemId == R.id.action_edit) {
            item.isVisible = viewModel.isRefreshAvailable() && binding.toolbarEdit.hasFocus().not()
        } else if (item.isVisible.xor(visible)) {
            item.isVisible = visible
        }
    }

    override fun onDestroy() {
        binding.recyclerView.removeOnScrollListener(mScrollListener)
        binding.recyclerView.adapter = null
        viewModel.dialog.removeOnPropertyChangedCallback(mDialogObserver)
        viewModel.message.removeOnPropertyChangedCallback(mMessageObserver)
        super.onDestroy()
    }
}