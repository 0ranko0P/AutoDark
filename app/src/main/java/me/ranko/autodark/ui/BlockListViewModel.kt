package me.ranko.autodark.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserManager
import android.text.Editable
import android.text.TextWatcher
import android.util.ArrayMap
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.collection.ArraySet
import androidx.databinding.ObservableField
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.BLOCK_LIST_PATH
import me.ranko.autodark.Constant.PERMISSION_SEND_DARK_BROADCAST
import me.ranko.autodark.R
import me.ranko.autodark.Utils.FileUtil
import me.ranko.autodark.core.LoadStatus
import me.ranko.autodark.model.BaseBlockableApplication
import me.ranko.autodark.model.Blockable
import me.ranko.autodark.model.BlockableApplication
import me.ranko.autodark.receivers.BlockListReceiver
import me.ranko.autodark.receivers.BlockListReceiver.Companion.ACTION_SWITCH_INPUT_METHOD_RESULT
import me.ranko.autodark.receivers.BlockListReceiver.Companion.ACTION_UPDATE_PROGRESS
import me.ranko.autodark.receivers.BlockListReceiver.Companion.EXTRA_KEY_LIST_PROGRESS
import me.ranko.autodark.receivers.BlockListReceiver.Companion.EXTRA_KEY_SWITCH_RESULT
import me.ranko.autodark.receivers.InputMethodReceiver
import me.ranko.autodark.ui.MainViewModel.Companion.Summary
import timber.log.Timber
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.collections.ArrayList

class BlockListViewModel(application: Application) : AndroidViewModel(application), BlockListAdapter.AppSelectListener {

    companion object {

        private const val MAX_UPLOAD_TIME_MILLIS = 5000L

        private const val KEY_SHOW_SYSTEM_APP = "show_sys"
        private const val KEY_BLOCKED_FIRST = "blocked_first"

        class Factory(private val application: Application) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
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

        private var originList: Collection<Blockable> = emptyList()
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
            if (!edit.hasFocus()) return

            if (str.isEmpty()) {
                _mAppList.value = emptyList()
            } else {
                val result = LinkedList<Blockable>()
                // iterate old list if is appendSearch
                val appList = if (appendSearch) _mAppList.value!! else originList
                for (app in appList) {
                    if (app.getPackageName().contains(str, true)) {
                        result.add(app)
                    } else if (app is ApplicationInfo && getAppName(app).contains(str, true)) {
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
                originList = if (isEditing()) mBlockSet else _mAppList.value!!
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

    private val mBlockSet = ArraySet<BaseBlockableApplication>()

    private var timer: Instant = Instant.now()

    private val uploadTimeOutWatcher = AtomicReference<Job?>()

    private val _uploadStatus = MutableLiveData<@LoadStatus Int>()
    val uploadStatus: LiveData<Int>
        get() = _uploadStatus

    val updateMessage =  ObservableField<String>()

    /**
     * Indicates a refreshing job is running, UI should show a loading progress
     * and hide all the buttons.
     *
     * @see refreshList
     * */
    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean>
        get() = _isRefreshing

    private var _isSearching = MutableLiveData<Boolean>()
    val isSearching: LiveData<Boolean>
        get() = _isSearching

    private val _mAppList = MutableLiveData<Collection<Blockable>>()
    val mAppList: LiveData<Collection<Blockable>>
        get() = _mAppList

    private var _isEditing = MutableLiveData(false)
    val isEditing: LiveData<Boolean>
        get() = _isEditing

    /**
     * Temporary reference [_mAppList] when entering edit mode
     *
     * @see refreshList
     * */
    private var _mEditList: Collection<Blockable> = emptyList()

    private var mSearchHelper: SearchHelper? = null

    val dialog = ObservableField<DialogFragment?>()
    private var edXposedDialogShowed = false

    val message =  ObservableField<Summary?>()

    private val updateStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            Timber.i("onReceive: Response is: %s", intent.action)
            when (intent.action) {
                ACTION_UPDATE_PROGRESS -> onUpdateListResponse(intent)

                ACTION_SWITCH_INPUT_METHOD_RESULT -> onImeSwitchResponse(intent)
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_UPDATE_PROGRESS)
        filter.addAction(ACTION_SWITCH_INPUT_METHOD_RESULT)
        mContext.registerReceiver(updateStatusReceiver, filter, PERMISSION_SEND_DARK_BROADCAST, null)
    }

    fun getAppName(app: ApplicationInfo): String = app.loadLabel(mPackageManager).toString()

    fun attachSearchHelper(owner: LifecycleOwner, editText: EditText) {
        mSearchHelper = SearchHelper(owner, editText)

        if (edXposedDialogShowed.not() && ActivationScopeDialog.shouldShowEdXposedDialog(mPackageManager, sp)) {
            dialog.set(ActivationScopeDialog.newInstance(Files.exists(Constant.BLOCK_LIST_INPUT_METHOD_CONFIG_PATH)))
        }
        // only check once
        edXposedDialogShowed = true
    }

    @Suppress("UNCHECKED_CAST", "QueryPermissionsNeeded")
    suspend fun getInstalledApps(): Collection<BlockableApplication> = withContext(Dispatchers.IO) {
        val myApps = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA).map { BlockableApplication(it) }
        if (!UserManager.supportsMultipleUsers()) {
            Timber.i("No multi-user support")
            return@withContext myApps
        }

        val appMap = ArrayMap<String, BlockableApplication>()
        myApps.forEach { app -> appMap[app.packageName] = app }

        // avoid request Manifest.permission.MANAGE_USERS permission
        // use pattern to get user id
        val findInt = Pattern.compile("\\D+")
        try {
            val userManager = mContext.getSystemService(Context.USER_SERVICE) as UserManager
            val pkgMethod = mPackageManager::class.java.getMethod(
                "getInstalledApplicationsAsUser",
                Int::class.javaPrimitiveType, // flags
                Int::class.javaPrimitiveType // userId
            )

            for (user in userManager.userProfiles) {
                val uid = findInt.matcher(user.toString()).replaceAll("").toInt()
                if (uid <= android.os.Process.ROOT_UID) continue

                for (app in pkgMethod.invoke(mPackageManager, PackageManager.GET_META_DATA, uid) as List<ApplicationInfo>) {
                    ensureActive()
                    if (!appMap.contains(app.packageName)) {
                        appMap[app.packageName] = BlockableApplication(app, user, uid)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return@withContext appMap.values
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun refreshList(clearCurrent: Boolean = true) {
        if (isRefreshAvailable().not()) return

        _isRefreshing.value = true
        timer = Instant.now()
        viewModelScope.launch(Dispatchers.IO) {
            if (clearCurrent) {
                if (mBlockSet.isNotEmpty()) mBlockSet.clear()
                val blockedApps = FileUtil.readList(BLOCK_LIST_PATH)?.map {
                    BaseBlockableApplication(it)
                }
                blockedApps?.let { mBlockSet.addAll(it) }
            }

            if (isEditing()) {
                _mEditList = _mAppList.value!!
                _mAppList.postValue(mBlockSet)
            } else {
                _mAppList.postValue(loadAppList())
            }
            _isRefreshing.postValue(false)
        }
    }

    private suspend fun loadAppList(): List<BlockableApplication> {
        val showSysApp = shouldShowSystemApp()
        val blockFirst = isBlockedFirst()

        val resultMap: Map<Boolean, List<BlockableApplication>> = getInstalledApps()
            .stream()
            .filter { app -> showSysApp || app.isSysApp().not() || isAppBlocked(app) }
            .sorted { o1, o2 -> getAppName(o1).compareTo(getAppName(o2)) }
            .collect(Collectors.partitioningBy { app -> blockFirst && isAppBlocked(app) })

        val appList = resultMap[false]!!

        return if (blockFirst) {
            val blockList = resultMap[true]!!
            val blockFirstList = ArrayList<BlockableApplication>(blockList.size + appList.size)
            blockFirstList.addAll(blockList)
            blockFirstList.addAll(appList)
            blockFirstList
        } else {
            appList
        }
    }

    fun onEditMode() {
        _isEditing.value = _isEditing.value != true
        refreshList(false)
    }

    fun isEditing(): Boolean = _isEditing.value == true

    fun isRefreshAvailable(): Boolean {
        return _isRefreshing.value != true && isUploading().not() && _uploadStatus.value != LoadStatus.FAILED
    }

    fun isBlockedFirst(): Boolean = sp.getBoolean(KEY_BLOCKED_FIRST, true)

    fun shouldShowSystemApp(): Boolean = sp.getBoolean(KEY_SHOW_SYSTEM_APP, false)

    override fun onAppBlockStateChanged(app: Blockable): Boolean {
        return if (mBlockSet.contains(app)) {
            mBlockSet.remove(app)
            if (isEditing()) {
                _mAppList.value = _mAppList.value
            }
            false
        } else {
            mBlockSet.add(BaseBlockableApplication(app))
            if (isEditing()) {
                _mAppList.value = _mAppList.value
            }
            true
        }
    }

    override fun isAppBlocked(app: Blockable): Boolean = mBlockSet.contains(app)

    override fun onEditItemClicked(app: Blockable) {
        dialog.set(BlockListEditDialog.newInstance(app.getPackageName()))
    }

    fun onFabClicked(@Suppress("UNUSED_PARAMETER")v: View) {
        if (_isEditing.value == true) {
            dialog.set(BlockListEditDialog.newInstance(null))
        } else {
            requestUploadList()
        }
    }

    private fun requestUploadList() {
        if (isUploading()) {
            message.set(newSummary(R.string.app_upload_busy))
            return
        }

        startUpload("onRequestUploadList: Upload time out!")
        viewModelScope.launch(Dispatchers.IO) {
            val blockedPackages = mBlockSet.map { it.getPackageName() } as ArrayList
            try {
                Files.write(BLOCK_LIST_PATH, blockedPackages)
                BlockListReceiver.sendNewList(mContext, blockedPackages)
            } catch (e: Exception) {
                Timber.w(e, "Failed to write block list")
                stopUpload(false)
            }
        }
    }

    private fun onUpdateListResponse(intent: Intent) {
        when (intent.getIntExtra(EXTRA_KEY_LIST_PROGRESS, LoadStatus.FAILED)) {
            LoadStatus.SUCCEED -> stopUpload(true, mContext.getString(R.string.app_upload_success), this::refreshList)

            LoadStatus.FAILED -> stopUpload(false)

            LoadStatus.START -> {/* no-op */}
        }
    }

    fun isUploading(): Boolean = uploadStatus.value == LoadStatus.START

    private fun startUpload(timeOutMessage: String, message: String = mContext.getString(R.string.app_upload_start)) {
        timer = Instant.now()
        _uploadStatus.value = LoadStatus.START
        updateMessage.set(message)

        uploadTimeOutWatcher.set(viewModelScope.launch {
            delay(MAX_UPLOAD_TIME_MILLIS)

            if (uploadTimeOutWatcher.getAndSet(null) != null) {
                Timber.e(timeOutMessage)
                stopUpload(false)
            }
        })
    }

    private fun stopUpload(succeed: Boolean, msg: String = mContext.getString(R.string.app_upload_fail), doOnEnd: (() -> Unit)? = null) {
        val watcher = uploadTimeOutWatcher.getAndSet(null)
        if (watcher?.isActive == true) watcher.cancel()

        val cost = Duration.between(timer, Instant.now()).toMillis()
        viewModelScope.launch(Dispatchers.Main) {
            if (cost < 600L) delay(1000L) // wait longer
            if (succeed) {
                _uploadStatus.value = LoadStatus.SUCCEED
                message.set(Summary(msg))
            } else {
                _uploadStatus.value = LoadStatus.FAILED
                updateMessage.set(msg)
            }
            doOnEnd?.invoke()
        }
    }

    fun onShowSysAppSelected(selected: Boolean) {
        if (sp.edit().putBoolean(KEY_SHOW_SYSTEM_APP, selected).commit()) {
            refreshList(false)
        }
        if (selected) {
            message.set(newSummary(R.string.app_hook_system_message))
        }
    }

    fun onBlockFirstSelected(selected: Boolean) {
        if (sp.edit().putBoolean(KEY_BLOCKED_FIRST, selected).commit()) {
            refreshList(false)
        }
    }

    fun onHookImeSelected(menu: MenuItem) {
        val imeFlag: Path = Constant.BLOCK_LIST_INPUT_METHOD_CONFIG_PATH
        if (menu.isChecked.not() == Files.exists(imeFlag)) return

        startUpload("onRequestImeSwitch: time out waiting ImeHooker response.")
        viewModelScope.launch(Dispatchers.IO) {
            if (FileUtil.saveFlagAsFile(imeFlag, !menu.isChecked)) {
                BlockListReceiver.requestSwitchIME(mContext)
                menu.isChecked = !menu.isChecked
            } else {
                stopUpload(false)
            }
        }
    }

    private fun onImeSwitchResponse(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_KEY_SWITCH_RESULT, false)) {
            val messageStr = mContext.getString(R.string.app_hook_ime_restart, mContext.getString(R.string.inputmethod))
            stopUpload(true, messageStr)

            if (InputMethodReceiver.shouldHookIME()) {
                viewModelScope.launch(Dispatchers.Main) {
                    val cost = Duration.between(timer, Instant.now()).toMillis()
                    if (cost < 600L) delay(1600L) // wait for toast
                    dialog.set(ActivationScopeDialog.newInstance(true))
                }
            }
        } else {
            stopUpload(false)
        }
    }

    private fun newSummary(@StringRes message: Int) = Summary(mContext.getString(message))

    override fun onCleared() {
        super.onCleared()
        mContext.unregisterReceiver(updateStatusReceiver)
        mBlockSet.clear()
    }
}