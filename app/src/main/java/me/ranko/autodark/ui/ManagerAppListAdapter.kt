package me.ranko.autodark.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.ui.ManagerAppListAdapter.LSPoxedViewHolder
import timber.log.Timber

class ManagerAppListAdapter(context: Context) : RecyclerView.Adapter<LSPoxedViewHolder>() {

    companion object {
        private var DUMMY_VERSION: String? = null

        private var DUMMY_ICON: Drawable? = null

        private const val DUMMY_PACKAGE_NAME = "com.other.ignore"
        private const val INPUT_METHOD_PACKAGE_NAME = "com.ranko.inputmethod.latin"

        private const val SCOPED_APPS_CHECK_DELAY = 1000L

        private class DummyApplicationInfo(dummyName: String) : ApplicationInfo() {
            init {
                name = dummyName
                packageName = DUMMY_PACKAGE_NAME
            }

            override fun loadLabel(pm: PackageManager): CharSequence = name

            override fun loadIcon(pm: PackageManager?): Drawable = DUMMY_ICON!!
        }

        private class ImeApplicationInfo(imeName: String) : ApplicationInfo() {
            init {
                name = imeName
                packageName = INPUT_METHOD_PACKAGE_NAME
            }

            override fun loadLabel(pm: PackageManager): CharSequence = name

            override fun loadIcon(pm: PackageManager?): Drawable? = null
        }

        /**
         * Build dummy [ApplicationInfo] for demonstrating scoped app list in XposedManager.
         * */
        fun buildDummyApps(context: Context): List<ApplicationInfo> {
            val manager = context.applicationContext.packageManager
            val result = ArrayList<ApplicationInfo>(8)
            val system: ApplicationInfo = try {
                manager.getApplicationInfo(
                    Constant.ANDROID_PACKAGE,
                    PackageManager.MATCH_SYSTEM_ONLY
                )
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.i(e)
                DummyApplicationInfo("System Framework").apply {
                    packageName = Constant.ANDROID_PACKAGE
                }
            }
            result.add(system)

            // Create two dummy IME app
            val ime: ApplicationInfo = ImeApplicationInfo(context.getString(R.string.inputmethod))
            result.add(ime)
            result.add(ImeApplicationInfo(ime.name + '2'))
            // Fill result array with dummy apps
            val dummy: ApplicationInfo = DummyApplicationInfo(context.getString(R.string.other_app))
            repeat(8 - result.size) { result.add(dummy) }
            return result
        }
    }

    class LSPoxedViewHolder(root: View) : RecyclerView.ViewHolder(root) {

        private val recommended: TextView = root.findViewById(R.id.recommended)
        val name: TextView = root.findViewById(R.id.name)
        val id: TextView = root.findViewById(R.id.appID)
        val icon: ImageView = root.findViewById(R.id.icon)

        private val checkBox: CheckBox = root.findViewById(R.id.checkbox)
        private val version: TextView = root.findViewById(R.id.version)

        init {
            version.text = DUMMY_VERSION
        }

        fun bindDummy() {
            recommended.isGone = true
            id.isVisible = true
            version.isVisible = true
            checkBox.isChecked = false
        }

        fun bindSystemFrameWork() {
            checkBox.isChecked = true
            recommended.isGone = false
            version.isGone = true
        }

        fun bindIme(position: Int) {
            // make scoped app noticeable, so user won't add wrong app to scope
            if (checkBox.isChecked.not()) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(SCOPED_APPS_CHECK_DELAY + position * 120L)
                    checkBox.isChecked = true
                }
            }
            recommended.isGone = true
            version.isGone = false
            icon.setImageResource(R.drawable.ic_keyboard)
        }
    }

    private var list: List<ApplicationInfo> = buildDummyApps(context)
    private val inflater = LayoutInflater.from(context)
    private val pkgManager = context.packageManager

    init {
        if (DUMMY_ICON == null) {
            DUMMY_ICON = ContextCompat.getDrawable(context, android.R.mipmap.sym_def_app_icon)
            DUMMY_VERSION = context.getString(R.string.pref_version) + Build.VERSION.SDK_INT
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        list = emptyList()
        DUMMY_ICON = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LSPoxedViewHolder {
        return LSPoxedViewHolder(inflater.inflate(R.layout.item_manager_lsp, parent, false))
    }

    override fun onBindViewHolder(holder: LSPoxedViewHolder, position: Int) = with(holder) {
        val app = list[position]
        id.text = app.packageName
        name.text = app.loadLabel(pkgManager)
        icon.setImageDrawable(app.loadIcon(pkgManager))

        when (app.packageName) {
            DUMMY_PACKAGE_NAME -> bindDummy()

            Constant.ANDROID_PACKAGE -> bindSystemFrameWork()

            INPUT_METHOD_PACKAGE_NAME -> bindIme(position)
        }
    }

    override fun getItemCount(): Int = list.size
}