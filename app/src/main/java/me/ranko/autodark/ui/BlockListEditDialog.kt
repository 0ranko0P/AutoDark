package me.ranko.autodark.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil

class BlockListEditDialog : DialogFragment(), TextWatcher {

    companion object {
        private const val ARG_CURRENT_PACKAGE = "arg_c"
        private const val KEY_USER_INPUT = "edit_input"

        fun newInstance(currentPkg: String?): BlockListEditDialog {
            val args = Bundle()
            if (currentPkg != null) args.putString(ARG_CURRENT_PACKAGE, currentPkg)
            val fragment = BlockListEditDialog()
            fragment.arguments = args
            return fragment
        }

        private fun isPkgValid(s: CharSequence): Boolean {
            if (s.length <= 4 || !s[0].isAsciiLetter() || !s[s.length - 1].isAsciiLetter()) return false

            for (char in s) {
                if (char.isAsciiLetterOrDigit() || char == '.' || char == '_') {
                    continue
                } else {
                    return false
                }
            }
            return true
        }

        /**
         * ASCII version of [Char.isLetter]
         * */
        private fun Char.isAsciiLetter(): Boolean = toInt().run { this in 65..90 || this in 97..122}

        /**
         * ASCII version of [Char.isLetterOrDigit]
         * */
        private fun Char.isAsciiLetterOrDigit(): Boolean {
            return toInt().run { this in 65..90 || this in 97..122 || this in 48..57 }
        }
    }

    private val viewModel: BlockListViewModel by lazy(LazyThreadSafetyMode.NONE) {
        val activity = requireActivity()
        ViewModelProvider(activity, BlockListViewModel.Companion.Factory(activity.application)).get(BlockListViewModel::class.java)
    }

    private lateinit var confirmButton: Button
    private lateinit var inputText: TextInputEditText
    private lateinit var inputLayout: TextInputLayout

    private var currentPkg = ""

    override fun onAttach(context: Context) {
        super.onAttach(context)
        currentPkg = requireArguments().getString(ARG_CURRENT_PACKAGE, currentPkg)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val savedInput: String? = savedInstanceState?.getString(KEY_USER_INPUT, null)
        val dialog = AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_edit_package)
                .setTitle(R.string.block_edit_title)
                .setNegativeButton(android.R.string.cancel, null)
                .create()

        dialog.setCanceledOnTouchOutside(false)

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.ok)) { _, _ ->
            val newPkg = inputText.text.toString()
            if (currentPkg.isNotEmpty() && currentPkg != newPkg) {
                // remove old package
                viewModel.onAppBlockStateChanged(currentPkg)
            }
            viewModel.onAppBlockStateChanged(newPkg)
        }

        if (currentPkg.isNotEmpty()) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.app_delete)) { _, _ ->
                viewModel.onAppBlockStateChanged(currentPkg)
            }
        }

        dialog.setOnShowListener {
            inputLayout = dialog.findViewById(R.id.inputLayout)!!
            inputText = dialog.findViewById(R.id.inputText)!!
            confirmButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
                    .setTextColor(resources.getColor(R.color.material_red_800, requireContext().theme))

            // validate user input and lock positive button if invalid
            inputText.addTextChangedListener(this)
            // init input text
            when {
                savedInput != null -> inputText.setText(savedInput)

                currentPkg.isEmpty() -> confirmButton.isEnabled = false

                else -> inputText.setText(currentPkg)
            }
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_USER_INPUT, inputText.text?.toString())
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        // no-op
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        val valid = isPkgValid(s)
        if (confirmButton.isEnabled.xor(valid)) {
            confirmButton.isEnabled = valid
            ViewUtil.setStrikeFontStyle(confirmButton, confirmButton.isEnabled.not())
        }
        inputLayout.error = if (valid) null else getString(R.string.block_edit_package_error)
    }

    override fun afterTextChanged(s: Editable) {
        // no-op
    }
}