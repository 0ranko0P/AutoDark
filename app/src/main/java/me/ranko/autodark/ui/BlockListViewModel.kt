package me.ranko.autodark.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.EditText
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import kotlinx.coroutines.*
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.*
import me.ranko.autodark.Receivers.ActivityUpdateReceiver
import me.ranko.autodark.Receivers.ActivityUpdateReceiver.Companion.STATUS_LIST_LOAD_FAILED
import me.ranko.autodark.Receivers.ActivityUpdateReceiver.Companion.STATUS_LIST_LOAD_START
import me.ranko.autodark.Receivers.ActivityUpdateReceiver.Companion.STATUS_LIST_LOAD_SUCCEED
import me.ranko.autodark.Utils.FileUtil
import me.ranko.autodark.ui.BlockListAdapter.Companion.EMPTY_APP_LIST
import timber.log.Timber
import java.io.IOException
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

        class Factory(private val application: Application) : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(BlockListViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return BlockListViewModel(application) as T
                }
                throw IllegalArgumentException("Unable to construct viewModel")
            }
        }

        @JvmStatic
        fun isAppendChars(old: CharSequence, new: CharSequence): Boolean {
            if (old.isEmpty() || old.length >= new.length) return false
            for ((index, sChar) in old.withIndex()) {
                if (new[index] != sChar) return false
            }
            return true
        }

        @WorkerThread
        private fun saveFlagAsFile(flagPath: Path, flag: Boolean): Boolean {
            return try {
                if (flag) {
                    FileUtil.crateIfNotExists(flagPath, FileUtil.PERMISSION_764)
                } else {
                    Files.deleteIfExists(flagPath)
                }
                true
            } catch (e: IOException) {
                Timber.d(e)
                false
            }
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
    private var mPackageManager: PackageManager = application.packageManager

    private val mBlockSet = HashSet<String>()

    private var timer: Instant = Instant.now()
    private val uploadTimeOutWatcher = AtomicReference<Job?>()

    val uploadStatus = ObservableInt()

    private val _isRefreshing = MutableLiveData(false)
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
            if (intent.action == ActivityUpdateReceiver.ACTION_RELOAD_RESULT) {
                val response = intent.getIntExtra(ActivityUpdateReceiver.EXTRA_KEY_LIST_RESULT, -1)
                onNewResponse(response)
            } else {
                throw RuntimeException("Wtf action: " + intent.action)
            }
        }
    }

    init {
        refreshList()
        mContext.registerReceiver(
                updateStatusReceiver,
                IntentFilter(ActivityUpdateReceiver.ACTION_RELOAD_RESULT),
                PERMISSION_DARK_BROADCAST,
                null
        )
    }

    fun attachViewModel() {
        // show big progressBar on first init
        if (!isUploading() && _isRefreshing.value == true) {
            uploadStatus.set(JOB_STATUS_PENDING)
        }
    }

    /**
     * drawable cached by system anyway
     * */
    fun getAppIcon(app: ApplicationInfo): Drawable = mPackageManager.getApplicationIcon(app)

    fun getAppName(app: ApplicationInfo): String = app.loadLabel(mPackageManager).toString()

    fun refreshList() {
        if (isRefreshing.value == true) return

        viewModelScope.launch {
            _isRefreshing.value = true
            mBlockSet.clear()
            val list = async(Dispatchers.IO) {
                FileUtil.readList(BLOCK_LIST_PATH)?.let { mBlockSet.addAll(it) }
                delay(1000L)
                val hookSysApp = Files.exists(Constant.BLOCK_LIST_SYSTEM_APP_CONFIG_PATH)
                return@async mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .stream()
                        .filter { hookSysApp || ApplicationInfo.FLAG_SYSTEM.and(it.flags) != ApplicationInfo.FLAG_SYSTEM }
                        .sorted { o1, o2 -> getAppName(o1).compareTo(getAppName(o2)) }
                        .collect(Collectors.toList())
                        .apply { forEach { getAppIcon(it) } }
            }.await()
            _mAppList.value = list
            uploadStatus.set(-1)
            _isRefreshing.value = false
        }
    }

    fun isBlocked(app: String): Boolean = mBlockSet.contains(app)

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
        viewModelScope.launch {
            val succeed: Boolean = async(Dispatchers.IO) {
                try {
                    FileUtil.crateIfNotExists(BLOCK_LIST_PATH, FileUtil.PERMISSION_764)
                    Files.write(BLOCK_LIST_PATH, mBlockSet)
                    return@async true
                } catch (e: Exception) {
                    Timber.w(e, "Failed to write block list")
                    return@async false
                }
            }.await()
            if (succeed) {
                ActivityUpdateReceiver.sendNewList(mContext, ArrayList(mBlockSet))
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

    fun onNewResponse(response: Int) {
        Timber.d("onNewResponse: $response")
        if (response == STATUS_LIST_LOAD_START) return

        // stop watcher now
        val watcher = uploadTimeOutWatcher.getAndSet(null)
        if (watcher?.isActive == true) watcher.cancel("Response received: $response")

        uploadStatus.set(if (response == STATUS_LIST_LOAD_SUCCEED) JOB_STATUS_SUCCEED else JOB_STATUS_FAILED)

        if (BuildConfig.DEBUG) {
            mContext.sendBroadcast(Intent(ActivityUpdateReceiver.ACTION_SERVER_PRINT_LIST))
            val cost = Duration.between(timer, Instant.now()).toMillis()
            Timber.w("${if (response == STATUS_LIST_LOAD_SUCCEED) "Succeed" else "Failed"}: ${cost}ms")
        }
    }

    fun isUploading(): Boolean = uploadStatus.get() == JOB_STATUS_PENDING

    fun updateMenuFlag(menu: MenuItem, flagPath: Path, onResult: Consumer<Boolean>?) {
        if (menu.isChecked.not() == Files.exists(flagPath)) return
        menu.isEnabled = false

        viewModelScope.launch {
            val result = async(Dispatchers.IO) {
                val rec = saveFlagAsFile(flagPath, !menu.isChecked)
                delay(900L) // wait for animation
                return@async rec
            }.await()

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