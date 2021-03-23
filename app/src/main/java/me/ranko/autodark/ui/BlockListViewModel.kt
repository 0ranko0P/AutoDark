package me.ranko.autodark.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.databinding.ObservableField
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.BLOCK_LIST_PATH
import me.ranko.autodark.Constant.PERMISSION_SEND_DARK_BROADCAST
import me.ranko.autodark.R
import me.ranko.autodark.Receivers.BlockListReceiver
import me.ranko.autodark.Utils.FileUtil
import me.ranko.autodark.core.LoadStatus
import me.ranko.autodark.ui.MainViewModel.Companion.Summary
import timber.log.Timber
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class BlockListViewModel(application: Application) : AndroidViewModel(application) {

    companion object {

        private const val MAX_UPLOAD_TIME_MILLIS = 5000L

        private const val KEY_SHOW_SYSTEM_APP = "show_sys"

        class Factory(private val application: Application) : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(BlockListViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return BlockListViewModel(application) as T
                }
                throw IllegalArgumentException("Unable to construct viewModel")
            }
        }
    }

    private inner class SearchHelper(owner: LifecycleOwner, private val edit: EditText) : TextWatcher,
            DefaultLifecycleObserver, View.OnFocusChangeListener {

        private var originList: List<ApplicationInfo> = emptyList()
        private var appendSearch = false

        init {
            owner.lifecycle.addObserver(this)
            edit.onFocusChangeListener = this
            edit.addTextChangedListener(this)
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            appendSearch = count != 0 && after > count && start == 0
        }

        override fun onTextChanged(str: CharSequence, start: Int, before: Int, count: Int) {
            if (str.isEmpty()) {
                _mAppList.value = emptyList()
            } else {
                val result = LinkedList<ApplicationInfo>()
                // iterate old list if isAppendInput
                val appList = if (appendSearch) _mAppList.value!! else originList
                for (app in appList) {
                    if (app.packageName.contains(str, true) || getAppName(app).contains(str, true)) {
                        result.add(app)
                    }
                }

                _mAppList.value = ArrayList(result)
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            edit.onFocusChangeListener = null
            edit.removeTextChangedListener(this)
            originList = emptyList()
            mSearchHelper = null
        }

        override fun onFocusChange(v: View, hasFocus: Boolean) {
            _isSearching.value = hasFocus
            if (hasFocus) {
                originList = _mAppList.value!!
                _mAppList.value = emptyList()
            } else {
                edit.text.clear()
                _mAppList.value = originList
            }
        }

        override fun afterTextChanged(s: Editable?) {
            // no-op
        }
    }

    private val mContext = application

    private val sp = PreferenceManager.getDefaultSharedPreferences(mContext)

    private val mPackageManager by lazy (LazyThreadSafetyMode.NONE) { mContext.packageManager }

    private val mBlockSet = HashSet<String>()

    private var timer: Instant = Instant.now()

    private val uploadTimeOutWatcher = AtomicReference<Job?>()

    private val _uploadStatus = MutableLiveData<@LoadStatus Int>()
    val uploadStatus: LiveData<Int>
        get() = _uploadStatus

    val updateMessage =  ObservableField<String>()

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean>
        get() = _isRefreshing

    private var _isSearching = MutableLiveData<Boolean>()
    val isSearching: LiveData<Boolean>
        get() = _isSearching

    private val _mAppList = MutableLiveData<List<ApplicationInfo>>()
    val mAppList: LiveData<List<ApplicationInfo>>
        get() = _mAppList

    private var mSearchHelper: SearchHelper? = null

    val dialog = ObservableField<DialogFragment?>()
    private var edXposedDialogShowed = false

    val message =  ObservableField<Summary?>()

    private val updateStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == BlockListReceiver.ACTION_UPDATE_PROGRESS) {
                val response = intent.getIntExtra(BlockListReceiver.EXTRA_KEY_LIST_PROGRESS, LoadStatus.FAILED)
                onNewResponse(response)
            } else {
                throw RuntimeException("Wtf action: " + intent.action)
            }
        }
    }

    init {
        mContext.registerReceiver(
            updateStatusReceiver,
            IntentFilter(BlockListReceiver.ACTION_UPDATE_PROGRESS),
            PERMISSION_SEND_DARK_BROADCAST, null
        )
    }

    /**
     * drawable cached by system anyway
     * */
    fun getAppIcon(app: ApplicationInfo): Drawable = mPackageManager.getApplicationIcon(app)

    fun getAppName(app: ApplicationInfo): String = app.loadLabel(mPackageManager).toString()

    fun attachSearchHelper(owner: LifecycleOwner, editText: EditText) {
        mSearchHelper = SearchHelper(owner, editText)

        if (edXposedDialogShowed.not() && ActivationScopeDialog.shouldShowEdXposedDialog(mPackageManager, sp)) {
            dialog.set(ActivationScopeDialog.newInstance(Files.exists(Constant.BLOCK_LIST_INPUT_METHOD_CONFIG_PATH)))
        }
        // only check once
        edXposedDialogShowed = true
    }

    fun refreshList() {
        if (_isRefreshing.value == true || isUploading()) return

        _isRefreshing.value = true
        viewModelScope.launch {
            if (mBlockSet.isNotEmpty()) mBlockSet.clear()

            val list = withContext(Dispatchers.IO) {
                FileUtil.readList(BLOCK_LIST_PATH)?.let { mBlockSet.addAll(it) }
                delay(1000L)
                val hookSysApp = shouldShowSystemApp()
                return@withContext mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .stream()
                        .filter { hookSysApp || ApplicationInfo.FLAG_SYSTEM.and(it.flags) != ApplicationInfo.FLAG_SYSTEM }
                        .sorted { o1, o2 -> getAppName(o1).compareTo(getAppName(o2)) }
                        .collect(Collectors.toList())
                        .onEach { getAppIcon(it) }
            }
            _mAppList.value = list
            _isRefreshing.value = false
        }
    }

    fun isBlocked(app: String): Boolean = mBlockSet.contains(app)

    fun shouldShowSystemApp(): Boolean = sp.getBoolean(KEY_SHOW_SYSTEM_APP, false)

    fun onAppSelected(app: ApplicationInfo): Boolean {
        val pkg = app.packageName
        return if (mBlockSet.contains(pkg)) {
            mBlockSet.remove(pkg)
            false
        } else {
            mBlockSet.add(pkg)
            true
        }
    }

    fun requestUploadList() {
        if (isUploading()) {
            message.set(newSummary(R.string.app_upload_busy))
            return
        }
        timer = Instant.now()

        _uploadStatus.value = LoadStatus.START
        updateMessage.set(mContext.getString(R.string.app_upload_start))

        viewModelScope.launch(Dispatchers.Main) {
            val succeed = withContext(Dispatchers.IO) {
                try {
                    Files.write(BLOCK_LIST_PATH, mBlockSet)
                    true
                } catch (e: Exception) {
                    Timber.w(e, "Failed to write block list")
                    false
                }
            }
            if (succeed) {
                BlockListReceiver.sendNewList(mContext, ArrayList(mBlockSet))
                uploadTimeOutWatcher.set(launch {
                    delay(MAX_UPLOAD_TIME_MILLIS)

                    if (uploadTimeOutWatcher.getAndSet(null) != null) {
                        // system server no response, wtf happened
                        Timber.e("onRequestUploadList: Upload time out!")
                        _uploadStatus.value = LoadStatus.FAILED
                        updateMessage.set(mContext.getString(R.string.app_upload_fail))
                    }
                })
            } else {
                _uploadStatus.value = LoadStatus.FAILED
                updateMessage.set(mContext.getString(R.string.app_upload_fail))
            }
        }
    }

    private fun onNewResponse(@LoadStatus response: Int) {
        if (response == LoadStatus.START) return

        // stop watcher now
        val watcher = uploadTimeOutWatcher.getAndSet(null)
        if (watcher?.isActive == true) watcher.cancel("Response received: $response")

        val cost = Duration.between(timer, Instant.now()).toMillis()
        Timber.i("onNewResponse: Response time cost: ${cost}ms")
        viewModelScope.launch(Dispatchers.Main) {
            if (cost < 600L) SystemClock.sleep(1000L) // wait longer
            if (response == LoadStatus.SUCCEED) {
                _uploadStatus.value = LoadStatus.SUCCEED
                message.set(newSummary(R.string.app_upload_success))
            } else {
                _uploadStatus.value = LoadStatus.FAILED
                updateMessage.set(mContext.getString(R.string.app_upload_fail))
            }
        }
    }

    fun isUploading(): Boolean = uploadStatus.value == LoadStatus.START

    fun onShowSysAppSelected(selected: Boolean) {
        if (sp.edit().putBoolean(KEY_SHOW_SYSTEM_APP, selected).commit()) {
            refreshList()
        }
        if (selected) {
            message.set(newSummary(R.string.app_hook_system_message))
        }
    }

    fun onHookImeSelected(menu: MenuItem) = viewModelScope.launch(Dispatchers.Main) {
        val imeFlag: Path = Constant.BLOCK_LIST_INPUT_METHOD_CONFIG_PATH
        if (menu.isChecked.not() == Files.exists(imeFlag)) return@launch
        menu.isEnabled = false

        val result = withContext(Dispatchers.IO) { FileUtil.saveFlagAsFile(imeFlag, !menu.isChecked) }
        val resultMessage = if (result) {
            menu.isChecked = !menu.isChecked
            R.string.app_hook_ime_restart
        } else {
            R.string.app_upload_fail
        }
        menu.isEnabled = true
        message.set(newSummary(resultMessage))
        if (menu.isChecked) {
            delay(600L) // wait for toast
            dialog.set(ActivationScopeDialog.newInstance(true))
        }
    }

    private fun newSummary(@StringRes message: Int) = Summary(mContext.getString(message))

    override fun onCleared() {
        super.onCleared()
        mContext.unregisterReceiver(updateStatusReceiver)
        mBlockSet.clear()
    }
}