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
import android.view.View
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import kotlinx.coroutines.*
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant.*
import me.ranko.autodark.Receivers.ActivityUpdateReceiver
import me.ranko.autodark.Receivers.ActivityUpdateReceiver.Companion.STATUS_LIST_LOAD_FAILED
import me.ranko.autodark.Receivers.ActivityUpdateReceiver.Companion.STATUS_LIST_LOAD_START
import me.ranko.autodark.Receivers.ActivityUpdateReceiver.Companion.STATUS_LIST_LOAD_SUCCEED
import me.ranko.autodark.Utils.FileUtil
import timber.log.Timber
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

        val EMPTY_APP_LIST = ArrayList<ApplicationInfo>(0)

        @JvmStatic
        fun isAppendChars(old: CharSequence, new: CharSequence): Boolean {
            if (old.isEmpty() || old.length >= new.length) return false
            for ((index, sChar) in old.withIndex()) {
                if (new[index] != sChar) return false
            }
            return true
        }
    }

    private val mContext = application
    private var mPackageManager: PackageManager = application.packageManager

    private val mBlockSet = HashSet<String>()

    private var timer: Instant = Instant.now()

    val uploadStatus = ObservableInt()

    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean>
        get() = _isSearching

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean>
        get() = _isRefreshing

    val appList = ObservableField<List<ApplicationInfo>>()

    private val mSearchHelper = object : TextWatcher, View.OnFocusChangeListener {
        var mEdit: TextView? = null
        var queryJob: Job? = null
        var lastInput: CharSequence = ""
        var mAppList: List<ApplicationInfo> = EMPTY_APP_LIST

        override fun onFocusChange(v: View, hasFocus: Boolean) {
            _isSearching.value = hasFocus
            // clear app list if searching
            if (hasFocus) {
                mAppList = appList.get()!!
                appList.set(EMPTY_APP_LIST)
            } else {
                if (queryJob?.isActive == true) queryJob?.cancel()
                appList.set(mAppList)
            }
        }

        override fun onTextChanged(str: CharSequence, start: Int, before: Int, count: Int) {
            if (str.isEmpty()) { // clear
                appList.set(EMPTY_APP_LIST)
                return
            }
            queryAndShow(str, isAppendChars(lastInput, str))
            lastInput = str.toString()
        }

        private fun queryAndShow(str: CharSequence, isAppend: Boolean) {
            val result = ArrayList<ApplicationInfo>()
            // iterate old list if isAppend
            val list = if (isAppend) appList.get()!! else mAppList
            for (app in list) {
                if (app.packageName.contains(str, true) || getAppName(app).contains(str, true)) {
                    result.add(app)
                }
            }

            appList.set(result)
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun afterTextChanged(s: Editable?) {
        }

        fun attach(editText: TextView) {
            mEdit = editText
            editText.text = null
            editText.onFocusChangeListener = this
            editText.addTextChangedListener(this)
        }

        fun detach() {
            mEdit?.onFocusChangeListener = null
            mEdit?.removeTextChangedListener(this)
            mEdit = null
        }
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

    fun attachViewModel(editText: TextView) {
        mSearchHelper.attach(editText)
    }

    fun detach() = mSearchHelper.detach()

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
                return@async mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .stream()
                        .filter { ApplicationInfo.FLAG_SYSTEM.and(it.flags) != ApplicationInfo.FLAG_SYSTEM }
                        .sorted { o1, o2 -> getAppName(o1).compareTo(getAppName(o2)) }
                        .collect(Collectors.toList())
                        .apply { forEach { getAppIcon(it) } }
            }.await()
            appList.set(list)
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

    override fun onCleared() {
        super.onCleared()
        mContext.unregisterReceiver(updateStatusReceiver)
        mBlockSet.clear()
    }
}