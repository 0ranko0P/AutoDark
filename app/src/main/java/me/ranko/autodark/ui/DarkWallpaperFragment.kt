package me.ranko.autodark.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.databinding.Observable
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import com.android.wallpaper.asset.Asset
import com.android.wallpaper.asset.LiveWallpaperThumbAsset
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.WallpaperPersister.DEST_BOTH
import com.android.wallpaper.picker.PreviewFragment
import com.android.wallpaper.widget.BottomActionBar
import com.android.wallpaper.widget.BottomActionBar.BottomAction
import com.android.wallpaper.widget.WallpaperColorsLoader
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ranko.autodark.R
import me.ranko.autodark.core.LoadStatus
import me.ranko.autodark.databinding.FragmentDarkWallpaperBinding
import me.ranko.autodark.model.CroppedWallpaperInfo
import me.ranko.autodark.ui.DarkWallpaperPickerViewModel.WallpaperRequest
import me.ranko.autodark.ui.widget.WallpaperPreviewLayout

/**
 * Fragment to display wallpapers of both light and dark mode.
 * */
class DarkWallpaperFragment : PreviewFragment(), ViewTreeObserver.OnGlobalLayoutListener {

    private class PreviewPagerAdapter(
            private var mPages: List<WallpaperPreviewLayout>,
            private val mListener: View.OnClickListener
    ) : PagerAdapter() {

        @NonNull
        override fun instantiateItem(@NonNull container: ViewGroup, position: Int): Any {
            val view = mPages[position]
            container.addView(view)
            view.setOnClickListener(mListener)
            return view
        }

        override fun getCount(): Int = mPages.size

        override fun destroyItem(@NonNull container: ViewGroup, position: Int, @NonNull item: Any) {
            container.removeView(item as WallpaperPreviewLayout)
            item.setOnClickListener(null)
            item.removeAllViews()
        }

        override fun isViewFromObject(@NonNull view: View, @NonNull o: Any): Boolean = view === o

        fun destroy() {
            mPages = emptyList()
            notifyDataSetChanged()
        }
    }

    private class GridPaddingDecoration constructor(private val mPadding: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position: Int = parent.getChildAdapterPosition(view)
            if (position >= 0) {
                outRect.left = mPadding
                outRect.right = mPadding
            }
        }
    }

    /**
     * Display all installed Live Wallpapers inside a [BottomActionBar]
     *
     * @see BottomActionBar.attachViewToBottomSheetAndBindAction
     * */
    private inner class LiveWallpaperBrowser(context: Context, var lastPos: Int?) : BottomSheetBehavior.BottomSheetCallback() {

        private val mLayoutManager = GridLayoutManager(context, BROWSER_DEFAULT_GRID_SPAN_COUNT)
        private val mRecycler = LayoutInflater.from(context).inflate(R.layout.recycler_live_wallpaper,
                mBinding.bottomActionbar.findViewById(R.id.action_bottom_sheet), false) as RecyclerView

        private val mAdapter: LiveWallpaperAdapter
        private val preloader: RecyclerViewPreloader<LiveWallpaperThumbAsset>

        /**
         * Change nav bar color when bottomBar expand
         * */
        private val originNavBar = requireActivity().window.navigationBarColor
        private val bottomBarNavBar = context.getColor(R.color.bottom_sheet_background)

        private val behavior = BottomSheetBehavior.from(mBinding.bottomActionbar.findViewById(R.id.action_bottom_sheet))

        init {
            mRecycler.layoutManager = mLayoutManager
            mRecycler.addItemDecoration(GridPaddingDecoration(resources.getDimensionPixelSize(R.dimen.grid_padding)))
            val sizeProvider = ViewPreloadSizeProvider<LiveWallpaperThumbAsset>()
            val requestManager = Glide.with(this@DarkWallpaperFragment)

            mAdapter = LiveWallpaperAdapter(context,
                    { newWallpaper -> viewModel.onWallpaperPicked(newWallpaper) },
                    requestManager,
                    sizeProvider)

            preloader = RecyclerViewPreloader(requestManager, mAdapter, sizeProvider, BROWSER_DEFAULT_GRID_SPAN_COUNT * 2)
            mRecycler.addOnScrollListener(preloader)

            mRecycler.setRecyclerListener { holder ->
                val viewHolder = holder as LiveWallpaperAdapter.ViewHolder
                Glide.with(mRecycler).clear(viewHolder.mImageView)
            }
            mRecycler.setItemViewCacheSize(0)
            mRecycler.adapter = mAdapter
            mBinding.bottomActionbar.attachViewToBottomSheetAndBindAction(mRecycler, BottomAction.APPLY, true)
            behavior.addBottomSheetCallback(this)
        }

        fun setWallpapers(wallpapers: List<LiveWallpaperInfo>) {
            mAdapter.setLiveWallpapers(wallpapers)
            val pos = lastPos
            if (pos != null && pos > 0) {
                mLayoutManager.scrollToPosition(pos)
                lastPos = null
            }
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            // hide action bar when collapsed
            if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                // ignore state changes from parent
                if (viewModel.wallpaperPickRequest.value != WallpaperRequest.LIVE_WALLPAPER_DISMISS) {
                    viewModel.onDismissLiveWallpaperPicker()
                }
            }
        }

        fun show() {
            if (mBinding.bottomActionbar.isVisible.not()) {
                requireActivity().window.navigationBarColor = bottomBarNavBar
                mBinding.bottomActionbar.show()
                mBinding.bottomActionbar.animate()
                        .setDuration(resources.getInteger(android.R.integer.config_mediumAnimTime).toLong())
                        .alpha(1f)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                mBinding.bottomActionbar.selectAction(BottomAction.APPLY)
                            }
                        })
            }
        }

        fun hide() {
            if (mBinding.bottomActionbar.isVisible.not()) return
            mBinding.bottomActionbar.deselectAction(BottomAction.APPLY)
            activity?.window?.navigationBarColor = originNavBar
            mBinding.bottomActionbar.animate()
                    .setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                    .alpha(0f)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            mBinding.bottomActionbar.hide()
                        }
                    })
        }

        fun save(outState: Bundle) {
            outState.putInt(BROWSER_STATE_POSITION_INDEX, mLayoutManager.findFirstVisibleItemPosition())
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            // no-op
        }

        fun destroy() {
            mRecycler.setRecyclerListener(null)
            mRecycler.removeOnScrollListener(preloader)
            mRecycler.adapter = null
            mAdapter.setLiveWallpapers(emptyList())
            behavior.removeBottomSheetCallback(this)
            mLiveWallpaperBrowser = null
        }
    }

    /**
     * Observe wallpaper changes from viewModel and load wallpapers in to [WallpaperPreviewLayout]
     *
     * @see [DarkWallpaperPickerViewModel.onWallpaperPicked]
     * @see [DarkWallpaperPickerViewModel.pickedLightWallpapers]
     * @see [DarkWallpaperPickerViewModel.pickedDarkWallpapers]
     * */
    private class WallpaperObserver(private val requestManager: RequestManager,
                                    private val glideRequestListener: RequestListener<Drawable>,
                                    private val croppedWallpaperOptions: RequestOptions,
                                    private val normalWallpaperOptions: RequestOptions,
                                    private val mPreview: WallpaperPreviewLayout) :
            Observer<Pair<WallpaperInfo, WallpaperInfo>> {

        private val context = mPreview.homeView.context.applicationContext

        override fun onChanged(wallpapers: Pair<WallpaperInfo, WallpaperInfo>) {
            load(wallpapers.first, mPreview.homeView)
            val lockAsset = load(wallpapers.second, mPreview.lockView)

            WallpaperColorsLoader.getWallpaperColors(context, lockAsset, mPreview.lockScreenPreviewer::setColor)
        }

        private fun load(wallpaper: WallpaperInfo, imageView: ImageView): Asset {
            val asset = if (wallpaper is LiveWallpaperInfo) {
                wallpaper.getThumbAsset(context)
            } else {
                wallpaper.getAsset(context)
            }
            val options = if (wallpaper is CroppedWallpaperInfo) croppedWallpaperOptions else normalWallpaperOptions

            requestManager.load(asset)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .error(R.drawable.ic_attention)
                    .addListener(glideRequestListener)
                    .into(imageView)

           return asset
        }

        fun destroy(target: LiveData<Pair<WallpaperInfo, WallpaperInfo>>) {
            target.removeObserver(this)
            mPreview.setHomeWallpaper(null)
            mPreview.setLockWallpaper(null)
        }
    }

    private lateinit var mBinding: FragmentDarkWallpaperBinding
    private lateinit var viewModel: DarkWallpaperPickerViewModel

    private lateinit var mAdapter: PreviewPagerAdapter
    private val mPageViews = ArrayList<WallpaperPreviewLayout>(2)

    private var mLiveWallpaperBrowser: LiveWallpaperBrowser? = null

    private var mMenuDelete: MenuItem? = null

    private lateinit var mLightWallpaperObserver: WallpaperObserver
    private lateinit var mDarkWallpaperObserver: WallpaperObserver

    private val mGlideRequestListener by lazy(LazyThreadSafetyMode.NONE) {
        object : RequestListener<Drawable> {
            override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                if (viewModel.isErrorAssetReported(model as Asset).not()) {
                    viewModel.onWallpaperCorrupted(model)
                    showLoadWallpaperErrorDialog(e)
                }
                return false
            }

            override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                return false
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel = ViewModelProvider(requireActivity(),
                DarkWallpaperPickerViewModel.Companion.Factory(requireActivity().application)).get()
    }

    override fun getLayoutResId(): Int = -1 // Using dataBinding

    @SuppressLint("MissingSuperCall")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mBinding = FragmentDarkWallpaperBinding.inflate(inflater, container, false)
        mBinding.viewModel = viewModel
        mBinding.lifecycleOwner = this
        return mBinding.root
    }

    override fun onBottomActionBarReady(bottomActionBar: BottomActionBar) {
        super.onBottomActionBarReady(bottomActionBar)

        // PreviewPager only contains two page:
        // 0: Light wallpaper page
        // 1: Dark wallpaper page
        val mLightPreview = WallpaperPreviewLayout(bottomActionBar.context, null)
        val mDarkPreview = WallpaperPreviewLayout(bottomActionBar.context, null)
        mPageViews.add(mLightPreview)
        mPageViews.add(mDarkPreview)

        val requestCategoryListener = View.OnClickListener { viewModel.requestCategory() }
        mAdapter = PreviewPagerAdapter(mPageViews, requestCategoryListener)
        mBinding.btnPick.setOnClickListener(requestCategoryListener)

        mBinding.previewPager.setAdapter(mAdapter)
        mBinding.previewPager.setOnPageChangeListener(viewModel)
        // set default page to Light wallpaper
        viewModel.onPageSelected(0)

        mLightPreview.attachLifecycle(lifecycle, requireActivity())
        mDarkPreview.attachLifecycle(lifecycle, requireActivity())

        mPageViews[1].lockView.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = mBinding.root.findViewById<Toolbar>(R.id.toolbar)
        toolbar.removeAllViews()
        setUpToolbar(toolbar)

        val activity = requireActivity() as DarkWallpaperPickerActivity
        activity.setSupportActionBar(toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setBackgroundColor(activity.window.statusBarColor)

        if (savedInstanceState != null) {
            val pos = savedInstanceState.getInt(BROWSER_STATE_POSITION_INDEX, -1)
            if (pos != -1) {
                showLiveWallpaperBrowser(pos)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_dark_wallpaper, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        mMenuDelete = menu.findItem(R.id.action_delete)
        mMenuDelete?.title = getString(R.string.delete_wallpapers, getString(R.string.pref_dark_wallpaper_title))
        viewModel.deleteAvailable.observe(this, Observer { available ->
            mMenuDelete?.isVisible = available
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finishActivity()

            R.id.action_delete -> showDeleteConfirmDialog()

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(requireActivity())
            .setTitle(mMenuDelete?.title)
            .setMessage(R.string.delete_wallpapers_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.deleteAll() }
            .show()
    }

    override fun onGlobalLayout() {
        val darkImageView = mPageViews[1].lockView

        darkImageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
        val placeHolderDrawable = Asset.getPlaceholderDrawable(
                requireContext(),
                darkImageView,
                requireContext().getColor(R.color.bottom_sheet_background))

        val normalWallpaperOptions = RequestOptions.centerCropTransform().placeholder(placeHolderDrawable)
        val croppedWallpaperOptions = RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.NONE)
                .apply(normalWallpaperOptions)
                .skipMemoryCache(true)

        val requestManager = Glide.with(this)
        mLightWallpaperObserver = WallpaperObserver(requestManager,
                mGlideRequestListener,
                croppedWallpaperOptions,
                normalWallpaperOptions,
                mPageViews[0])

        mDarkWallpaperObserver = WallpaperObserver(requestManager,
                mGlideRequestListener,
                croppedWallpaperOptions,
                normalWallpaperOptions,
                mPageViews[1])

        viewModel.pickedLightWallpapers.observe(viewLifecycleOwner, mLightWallpaperObserver)
        viewModel.pickedDarkWallpapers.observe(viewLifecycleOwner, mDarkWallpaperObserver)

        viewModel.loadStatus.observe(viewLifecycleOwner, Observer { status ->
            if (status == LoadStatus.FAILED) {
                showSaveWallpaperErrorDialog(viewModel.getException(), DEST_BOTH)
            }
        })

        viewModel.message.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable, propertyId: Int) {
                val message = (sender as ObservableInt).get()
                Snackbar.make(mBinding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        })

        viewModel.wallpaperPickRequest.observe(viewLifecycleOwner, Observer { request ->
            when (request) {
                WallpaperRequest.CATEGORY_CHOOSER -> {
                    WallpaperCategoryDialog.newInstance(true)
                            .show(childFragmentManager, TAG_WALLPAPER_CATEGORY_FRAGMENT)
                }

                WallpaperRequest.CATEGORY_RESTRICTED -> {
                    WallpaperCategoryDialog.newInstance(false)
                            .show(childFragmentManager, TAG_WALLPAPER_CATEGORY_FRAGMENT)
                }

                WallpaperRequest.LIVE_WALLPAPER -> showLiveWallpaperBrowser(null)

                WallpaperRequest.LIVE_WALLPAPER_DISMISS -> hideLiveWallpaperBrowser()

                else -> {/* no-op */}
            }
        })

        viewModel.refreshWallpaperPreview()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mBinding.bottomActionbar.isVisible) {
            mLiveWallpaperBrowser!!.save(outState)
        }
    }

    private fun showLiveWallpaperBrowser(lastPos: Int?) {
        setTitle(getString(R.string.chooser_category_live_wallpaper))
        if (mLiveWallpaperBrowser == null) {
            mLiveWallpaperBrowser = LiveWallpaperBrowser(requireContext(), lastPos).apply {
                show()
                lifecycleScope.launch(Dispatchers.Main) {
                    setWallpapers(viewModel.getLiveWallpapersAsync().await())
                }
            }
        } else {
            mLiveWallpaperBrowser!!.show()
        }
    }

    private fun hideLiveWallpaperBrowser() {
        if (mLiveWallpaperBrowser != null) {
            setTitle(defaultTitle)
            mLiveWallpaperBrowser!!.hide()
        }
    }

    override fun getDefaultTitle(): CharSequence = getString(R.string.pref_dark_wallpaper_title)

    override fun onClickTryAgain(wallpaperDestination: Int) {
        viewModel.onApplyWallpaperClicked()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mBinding.previewPager.setOnPageChangeListener(null)
        mAdapter.destroy()
        mPageViews.clear()
        mBinding.previewPager.setAdapter(null)
        mLiveWallpaperBrowser?.destroy()
        mBinding.bottomActionbar.reset()
        mLightWallpaperObserver.destroy(viewModel.pickedLightWallpapers)
        mDarkWallpaperObserver.destroy(viewModel.pickedDarkWallpapers)
    }

    companion object {
        private const val TAG_WALLPAPER_CATEGORY_FRAGMENT = "fragChooser"

        private const val BROWSER_DEFAULT_GRID_SPAN_COUNT = 3
        private const val BROWSER_STATE_POSITION_INDEX = "posIndex"
    }
}