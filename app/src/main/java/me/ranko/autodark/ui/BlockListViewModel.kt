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
import android.widget.EditText
import androidx.annotation.MainThread
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import me.ranko.autodark.Constant.*
import me.ranko.autodark.Receivers.BlockListReceiver
import me.ranko.autodark.Utils.FileUtil
import me.ranko.autodark.core.LoadStatus
import me.ranko.autodark.ui.BlockListAdapter.Companion.EMPTY_APP_LIST
import timber.log.Timber
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.stream.Collectors

class BlockListViewModel(application: Application) : AndroidViewModel(application) {

    companion object {

        private const val MAX_UPLOAD_TIME_MILLIS = 9000L

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

        fun isAppendChars(old: CharSequence, new: CharSequence): Boolean {
            if (old.isEmpty() || old.length >= new.length) return false
            for ((index, sChar) in old.withIndex()) {
                if (new[index] != sChar) return false
            }
            return true
        }

        class SearchHelper(private val viewModel: BlockListViewModel) : TextWatcher,
            DefaultLifecycleObserver {
            private var _mAppList: MutableLiveData<List<ApplicationInfo>> = viewModel._mAppList
            private var list: List<ApplicationInfo> = EMPTY_APP_LIST
            private var mEdit: EditText? = null
            private var lastInput: String = ""

            override fun onTextChanged(str: CharSequence, start: Int, before: Int, count: Int) {
                val input = str.toString()
                if (lastInput == input) return
                lastInput = input

                if (input.isEmpty()) {
                    _mAppList.value = EMPTY_APP_LIST
                } else {
                    val isAppend = isAppendChars(lastInput, input)
                    val result = ArrayList<ApplicationInfo>()
                    // iterate old list if isAppendInput
                    val appList = if (isAppend) _mAppList.value!! else list
                    for (app in appList) {
                        if (app.packageName.contains(str, true) || viewModel.getAppName(app).contains(str, true)) {
                            result.add(app)
                        }
                    }

                    _mAppList.value = result
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
            }

            override fun onResume(owner: LifecycleOwner) {
                mEdit = (owner as BlockListActivity).getSearchEditText().also {
                    it.addTextChangedListener(this)
                    it.onFocusChangeListener = owner
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                mEdit?.onFocusChangeListener = null
                mEdit?.removeTextChangedListener(this)
                mEdit = null
            }

            fun startSearch() {
                if (lastInput.isEmpty()) {
                    list = _mAppList.value!!
                    _mAppList.value = EMPTY_APP_LIST
                }
            }

            fun stopSearch() {
                lastInput = ""
                mEdit?.clearFocus()
                mEdit?.text?.clear()
                _mAppList.value = list
            }
        }
    }

    private val mContext = application
    private var sp = PreferenceManager.getDefaultSharedPreferences(application)
    private var mPackageManager: PackageManager = application.packageManager

    private val mBlockSet = HashSet<String>()

    private var timer: Instant = Instant.now()
    private val uploadTimeOutWatcher = AtomicReference<Job?>()

    val uploadStatus = ObservableInt()

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean>
        get() = _isRefreshing

    private val _mAppList = MutableLiveData<List<ApplicationInfo>>(EMPTY_APP_LIST)
    val mAppList: LiveData<List<ApplicationInfo>>
        get() = _mAppList

    val mSearchHelper by lazy(LazyThreadSafetyMode.NONE) {
        SearchHelper(this)
    }

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

    fun onShowSysAppSelected(selected: Boolean) {
        if (sp.edit().putBoolean(KEY_SHOW_SYSTEM_APP, selected).commit()) {
            refreshList()
        }
    }

    fun refreshList() {
        if (_isRefreshing.value == true) return

        _isRefreshing.value = true
        viewModelScope.launch {
            mBlockSet.clear()
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
            uploadStatus.set(-1)
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

    @MainThread
    fun requestUploadList(): Boolean {
        if (isUploading()) return false
        timer = Instant.now()

        uploadStatus.set(JOB_STATUS_PENDING)
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
                        Timber.e("onRequestUploadList: Upload time out! status: ${uploadStatus.get()}")
                        uploadStatus.set(JOB_STATUS_FAILED)
                    }
                })
            } else {
                uploadStatus.set(JOB_STATUS_FAILED)
            }
        }
        return true
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
            uploadStatus.set(if (response == LoadStatus.SUCCEED) JOB_STATUS_SUCCEED else JOB_STATUS_FAILED)
        }
    }

    fun isUploading(): Boolean = uploadStatus.get() == JOB_STATUS_PENDING

    fun updateMenuFlag(menu: MenuItem, flagPath: Path, onResult: Consumer<Boolean>?) {
        if (menu.isChecked.not() == Files.exists(flagPath)) return
        menu.isEnabled = false

        viewModelScope.launch (Dispatchers.Main) {
            val result = withContext(Dispatchers.IO) {
                FileUtil.saveFlagAsFile(flagPath, !menu.isChecked)
            }
            if (result) {
                menu.isChecked = !menu.isChecked
            }
            menu.isEnabled = true
            onResult?.accept(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mContext.unregisterReceiver(updateStatusReceiver)
        mBlockSet.clear()
    }
}