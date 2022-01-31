package me.ranko.autodark.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.android.wallpaper.util.ScreenSizeCalculator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import me.ranko.autodark.R
import me.ranko.autodark.ui.widget.XposedManagerView

class ActivationScopeDialog : BottomSheetDialogFragment() {

    private lateinit var managerView: XposedManagerView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), R.style.AppTheme_BottomSheetDialogDayNight)
        dialog.setContentView(R.layout.dialog_activation_scope)
        dialog.setOnShowListener {
            val root = dialog.findViewById<View>(R.id.title)!!.parent as View
            val xposedContainer: ViewGroup = root.findViewById(R.id.container)
            managerView = XposedManagerView(requireActivity(), xposedContainer)
            root.findViewById<MaterialButton>(R.id.button).setOnClickListener { dismiss() }

            val screenSize = ScreenSizeCalculator.getInstance().getScreenSize(requireActivity())
            dialog.behavior.peekHeight = screenSize.y
        }
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        managerView.destroy()
    }
}