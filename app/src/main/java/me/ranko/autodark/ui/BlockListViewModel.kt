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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.annotation.MainThread
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import androidx.preference.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import me.ranko.autodark.Receivers.ActivityUpdateReceiver
import me.ranko.autodark.Receivers.ActivityUpdateReceiver.Companion.STATUS_LIST_LOAD_FAILED
import me.ranko.autodark.Receivers.ActivityUpdateReceiver.Companion.STATUS_LIST_LOAD_START
import me.ranko.autodark.Receivers.ActivityUpdateReceiver.Companion.STATUS_LIST_LOAD_SUCCEED
import me.ranko.autodark.Utils.FileUtil
import me.ranko.autodark.ui.BlockListAdapter.Companion.EMPTY_APP_LIST
import timber.log.Timber
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.stream.Collectors

class BlockListViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
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
            val result: Boolean = async(Dispatchers.IO) {
                try {
                    FileUtil.crateIfNotExists(BLOCK_LIST_PATH, FileUtil.PERMISSION_764)
                    Files.write(BLOCK_LIST_PATH, mBlockSet)
                    ActivityUpdateReceiver.sendNewList(mContext, ArrayList(mBlockSet))
                    delay(1000L)
                    // update success status when receive response
                    return@async true
                } catch (e: Exception) {
                    Timber.w(e, "Failed to write block list")
                    return@async false
                }
            }.await()
            if (!result) {
                uploadStatus.set(JOB_STATUS_FAILED)
            }
        }
        return true
    }

    fun onNewResponse(response: Int) {
        when (response) {
            STATUS_LIST_LOAD_START -> Timber.d("onReceiveSystemServer: Start")

            STATUS_LIST_LOAD_FAILED -> uploadStatus.set(JOB_STATUS_FAILED)

            STATUS_LIST_LOAD_SUCCEED -> uploadStatus.set(JOB_STATUS_SUCCEED)

            else -> throw IllegalArgumentException("WTF response: $response")
        }

        if (BuildConfig.DEBUG && response != STATUS_LIST_LOAD_START) {
            mContext.sendBroadcast(Intent(ActivityUpdateReceiver.ACTION_SERVER_PRINT_LIST))
            val cost = Duration.between(timer, Instant.now()).toMillis()
            Timber.w("${if (response == STATUS_LIST_LOAD_SUCCEED) "Succeed" else "Failed"}: ${cost}ms")
        }
    }

    fun isUploading(): Boolean = uploadStatus.get() == JOB_STATUS_PENDING

    fun onSysAppClicked(menu: MenuItem) {
        menu.isEnabled = false
        val exists = Files.exists(BLOCK_LIST_SYSTEM_APP_CONFIG_PATH)
        val exclude = exists.not()

        viewModelScope.launch {
            val result = async(Dispatchers.IO) {
                try {
                    if (exclude) {
                        FileUtil.crateIfNotExists(BLOCK_LIST_SYSTEM_APP_CONFIG_PATH, FileUtil.PERMISSION_764)
                    } else {
                        Files.deleteIfExists(BLOCK_LIST_SYSTEM_APP_CONFIG_PATH)
                    }
                    delay(900L) // wait for animation
                    return@async true
                } catch (e: IOException) {
                    Timber.d(e)
                    return@async false
                }
            }.await()

            if (result) {
                menu.isChecked = !menu.isChecked
                refreshList()
            } else {
                menu.isChecked = exists
            }
            menu.isEnabled = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        mContext.unregisterReceiver(updateStatusReceiver)
        mBlockSet.clear()
    }
}