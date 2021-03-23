package me.ranko.autodark.ui

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.android.wallpaper.util.ScreenSizeCalculator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import me.ranko.autodark.R
import me.ranko.autodark.ui.widget.XposedManagerView

class ActivationScopeDialog : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_IME_HOOKED = "arg_ime"

        private const val SP_EDXPOSED_DISMISS = "edx_hide"

        fun newInstance(imeHooked: Boolean): ActivationScopeDialog {
            val args = Bundle()
            args.putBoolean(ARG_IME_HOOKED, imeHooked)

            val fragment = ActivationScopeDialog()
            fragment.arguments = args
            return fragment
        }

        fun shouldShowEdXposedDialog(pkgManager: PackageManager, sp: SharedPreferences): Boolean {
            val type = XposedManagerView.getManagerType(pkgManager)
            return type == Manager.EDXposed && sp.getBoolean(SP_EDXPOSED_DISMISS, false).not()
        }
    }

    private var hookIme = false
    private lateinit var managerView: XposedManagerView

    override fun onAttach(context: Context) {
        super.onAttach(context)
        hookIme = requireArguments().getBoolean(ARG_IME_HOOKED, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), R.style.AppTheme_BottomSheetDialogDayNight)
        dialog.setContentView(R.layout.dialog_activation_scope)
        dialog.setOnShowListener {
            val root = dialog.findViewById<View>(R.id.title)!!.parent as View
            initView(root)

           val display = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
               requireActivity().display
           } else {
               (requireActivity().getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }
            val screenSize = ScreenSizeCalculator.getInstance().getScreenSize(display)
            dialog.behavior.peekHeight = screenSize.y
        }
        return dialog
    }

    private fun initView(view: View) {
        val xposedContainer: ViewGroup = view.findViewById(R.id.container)
        managerView = XposedManagerView(requireActivity(), xposedContainer, hookIme)

        val imeText = if (hookIme) getString(R.string.inputmethod) else ""
        // optional only if ime not hooked and running on EdXposed
        val optionalText = if (hookIme.not() && managerView.type == Manager.EDXposed) {
            getString(R.string.activation_optional)
        } else {
            getString(R.string.activation_required)
        }
        view.findViewById<TextView>(R.id.title).text = getString(R.string.activation_scope, imeText, optionalText)
        view.findViewById<TextView>(R.id.description).setText(R.string.activation_scope_description)

        view.findViewById<MaterialButton>(R.id.button).setOnClickListener { v ->
            if (managerView.type == Manager.EDXposed) {
                val sp = PreferenceManager.getDefaultSharedPreferences(v.context)
                sp.edit().putBoolean(SP_EDXPOSED_DISMISS, true).apply()
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        managerView.destroy()
    }
}