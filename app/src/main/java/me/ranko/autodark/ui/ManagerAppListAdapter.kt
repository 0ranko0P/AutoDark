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
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ranko.autodark.R
import me.ranko.autodark.ui.ManagerAppListAdapter.BaseViewHolder

enum class Manager(val pkg: String) {
    EDXposed("org.meowcat.edxposed.manager"),
    LSPosed("org.lsposed.manager")
}

class ManagerAppListAdapter(context: Context,
                            var list: List<ApplicationInfo>,
                            managerType: Manager) : RecyclerView.Adapter<BaseViewHolder>() {

    companion object {
        var managerType = Manager.LSPosed

        private var versionStr: String? = null

        private var DUMMY_ICON: Drawable? = null
        private const val DUMMY_PACKAGE_NAME = "com.other.ignore"
        private const val INPUT_METHOD_PACKAGE_NAME = "com.ranko.inputmethod.latin"

        private const val SCOPED_APPS_CHECK_DELAY = 1000L
    }

    class DummyApplicationInfo(dummyName: String) : ApplicationInfo() {
        init {
            name = dummyName
            packageName = DUMMY_PACKAGE_NAME
        }

        override fun loadLabel(pm: PackageManager): CharSequence = name

        fun getAppIcon(context: Context): Drawable {
            if (DUMMY_ICON == null) {
                DUMMY_ICON = ContextCompat.getDrawable(context, android.R.mipmap.sym_def_app_icon)
            }
            return DUMMY_ICON!!
        }
    }

    class ImeApplicationInfo(imeName: String) : ApplicationInfo() {
        init {
            name = imeName
            packageName = INPUT_METHOD_PACKAGE_NAME
        }

        override fun loadLabel(pm: PackageManager): CharSequence = name
    }

    abstract class BaseViewHolder(root: View, val manager: PackageManager) : RecyclerView.ViewHolder(root) {
        private val recommended: TextView = root.findViewById(R.id.recommended)

        val name: TextView
        val id: TextView
        val icon: ImageView

        init {
            val container = root.findViewById<View>(R.id.appListContainer)
            name = container.findViewById(R.id.name)
            id = container.findViewById(R.id.appID)
            icon = container.findViewById(R.id.icon)
        }

        open fun bind(app: ApplicationInfo, position: Int) {
            id.text = app.packageName
            name.text = app.loadLabel(manager)

            when (app) {
                is DummyApplicationInfo -> bindDummy(app)

                is ImeApplicationInfo -> {
                    bindScoped(app, position)
                    icon.setImageResource(R.drawable.ic_keyboard)
                    recommended.visibility = View.GONE
                }

                else -> {
                    bindScoped(app, position)
                    icon.setImageDrawable(manager.getApplicationIcon(app))
                }
            }
        }

        open fun bindDummy(dummy: DummyApplicationInfo) {
            name.text = dummy.name
            icon.setImageDrawable(dummy.getAppIcon(icon.context))
            if (recommended.visibility != View.GONE) recommended.visibility = View.GONE

            setChecked(false)
        }

        open fun bindScoped(app: ApplicationInfo, position: Int) {
            // make scoped app noticeable, so user won't add wrong app to scope
            if (isChecked().not()) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(SCOPED_APPS_CHECK_DELAY + position * 120L)
                    setChecked(true)
                }
            }
            recommended.visibility = View.VISIBLE
        }

        abstract fun setChecked(checked: Boolean)

        abstract fun isChecked(): Boolean

    }

    private class LSPoxedViewHolder(root: View, manager: PackageManager) : BaseViewHolder(root, manager) {
        private val checkBox: CheckBox = root.findViewById(R.id.checkbox)
        private val version: TextView = root.findViewById(R.id.version)

        init {
            version.text = getVersionStr()
        }

        override fun bindDummy(dummy: DummyApplicationInfo) {
            super.bindDummy(dummy)
            if (id.visibility != View.VISIBLE) id.visibility = View.VISIBLE
            if (version.visibility != View.VISIBLE) id.visibility = View.VISIBLE
        }

        override fun bindScoped(app: ApplicationInfo, position: Int) {
            super.bindScoped(app, position)
            if (app !is ImeApplicationInfo) {
                version.visibility = View.GONE
            }
        }

        override fun setChecked(checked: Boolean) {
            checkBox.isChecked = checked
        }

        override fun isChecked(): Boolean = checkBox.isChecked

        private fun getVersionStr(): String {
            if (versionStr == null) {
                val res = id.context.resources
                versionStr = res.getString(R.string.version_lsp, res.getString(R.string.pref_version), Build.VERSION.SDK_INT.toString())
            }
            return versionStr!!
        }
    }

    private class EDXposedViewHolder(root: View, manager: PackageManager) : BaseViewHolder(root, manager) {
        private val switchWidget: SwitchCompat = root.findViewById(R.id.switchWidget)

        init {
            val context = root.context
            root.findViewById<TextView>(R.id.installTime).text = context.getString(R.string.install_time, "2009-01-01")
            root.findViewById<TextView>(R.id.updateTime).text = context.getString(R.string.update_time, "2009-01-01")
            root.findViewById<TextView>(R.id.version).text = Build.VERSION.RELEASE
        }

        override fun setChecked(checked: Boolean) {
            switchWidget.isChecked = checked
        }

        override fun isChecked(): Boolean = switchWidget.isChecked
    }

    private val inflater = LayoutInflater.from(context)
    private val pkgManager = context.packageManager

    init {
        Companion.managerType = managerType
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        list = emptyList()
        DUMMY_ICON = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (managerType == Manager.EDXposed) {
            EDXposedViewHolder(inflater.inflate(R.layout.item_manager_edx, parent, false), pkgManager)
        } else {
            LSPoxedViewHolder(inflater.inflate(R.layout.item_manager_lsp, parent, false), pkgManager)
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind(list[position], position)
    }

    override fun getItemCount(): Int = list.size
}