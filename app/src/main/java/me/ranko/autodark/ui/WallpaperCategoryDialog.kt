package me.ranko.autodark.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.ui.DarkWallpaperPickerViewModel.WallpaperRequest.LIVE_WALLPAPER
import me.ranko.autodark.ui.DarkWallpaperPickerViewModel.WallpaperRequest.STATIC_WALLPAPER
import me.ranko.autodark.ui.widget.PermissionLayout

/**
 * A BottomSheetDialog asking the user for the Wallpaper's type, either [STATIC_WALLPAPER] or
 * [LIVE_WALLPAPER], And [LIVE_WALLPAPER] only available in Shizuku or Sui mode.
 * */
class WallpaperCategoryDialog : BottomSheetDialogFragment(), View.OnClickListener {

    companion object {
        const val ARG_SHIZUKU_AVAILABLE = "sa"

        fun newInstance(shizukuAvailable: Boolean): WallpaperCategoryDialog {
            val args = Bundle()
            args.putBoolean(ARG_SHIZUKU_AVAILABLE, shizukuAvailable)
            val instance = WallpaperCategoryDialog()
            instance.arguments = args
            return instance
        }
    }

    private lateinit var viewModel: DarkWallpaperPickerViewModel

    private var shizukuContainer: PermissionLayout? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel = ViewModelProvider(requireActivity(),
            DarkWallpaperPickerViewModel.Companion.Factory(requireActivity().application)).get()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.AppTheme_BottomSheetDialogDayNight)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_wallpaper_choose_category, container, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val btnWallpaper: MaterialButton = view.findViewById(R.id.btnWallpaper)
        val btnLiveWallpaper: MaterialButton = view.findViewById(R.id.btnLiveWallpaper)

        btnWallpaper.setOnClickListener(this)
        btnLiveWallpaper.setOnClickListener(this)

        val shizukuAvailable = requireArguments().getBoolean(ARG_SHIZUKU_AVAILABLE, false)
        if (shizukuAvailable.not()) {
            initShizukuPermissionCard(view)
            btnLiveWallpaper.isEnabled = false
            ViewUtil.setStrikeFontStyle(btnLiveWallpaper, true)
        }
    }

    /**
     * Called when Shizuku unavailable, inflate a [PermissionLayout] to waning user
     * that Live wallpaper are restricted.
     * */
    private fun initShizukuPermissionCard(root: View) {
        val stub = root.findViewById<ViewStub>(R.id.permissionStub)
        shizukuContainer = (stub.inflate() as PermissionLayout).apply {
            // Included layout, do some initialization here
            val shizukuOrSui = getString(R.string.shizuku_title) + "/" + getString(R.string.sui_title)
            setTitle(R.string.chooser_live_wallpaper_restricted_title)
            description = getString(R.string.chooser_live_wallpaper_restricted_description, shizukuOrSui)

            findViewById<View>(R.id.progressShizuku).visibility = View.GONE
            val btnShizuku = findViewById<TextView>(R.id.btnShizuku)
            btnShizuku.setCompoundDrawables(null, null, null, null)
            btnShizuku.setText(R.string.app_do_not_show)
            btnShizuku.gravity = Gravity.END
            btnShizuku.setOnClickListener(this@WallpaperCategoryDialog)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnWallpaper -> {
                dismiss()
                viewModel.onCategoryChosen(false)
            }

            R.id.btnLiveWallpaper -> {
                dismiss()
                viewModel.onCategoryChosen(true)
            }

            R.id.btnShizuku -> {
                // workaround for BottomSheet bug
                val dialogRoot = requireView().parent as FrameLayout
                val dialogParams = dialogRoot.layoutParams as CoordinatorLayout.LayoutParams
                dialogParams.gravity = Gravity.BOTTOM.or(Gravity.CENTER_HORIZONTAL)
                dialogRoot.layoutParams = dialogParams

                shizukuContainer?.visibility = View.GONE
                viewModel.onDismissShizukuWarring()
            }
        }
    }
}