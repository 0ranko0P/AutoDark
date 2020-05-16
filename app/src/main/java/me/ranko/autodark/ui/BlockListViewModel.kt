package me.ranko.autodark.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.annotation.MainThread
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    }

    private val mContext = application
    private var mPackageManager: PackageManager = application.packageManager

    private val mBlockSet = HashSet<String>()

    private var timer: Instant = Instant.now()

    private val _uploadStatus = MutableLiveData<Int?>()
    val uploadStatus: LiveData<Int?>
        get() = _uploadStatus

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

    fun register() {
        mContext.registerReceiver(
            updateStatusReceiver,
            IntentFilter(ActivityUpdateReceiver.ACTION_RELOAD_RESULT),
            PERMISSION_DARK_BROADCAST,
            null
        )
    }

    /**
     * drawable will be cached by system
     * */
    fun getAppIcon(app: ApplicationInfo): Drawable = mPackageManager.getApplicationIcon(app)

    fun getAppName(app: ApplicationInfo): String = app.loadLabel(mPackageManager).toString()

    fun reloadListAsync() = viewModelScope.async(Dispatchers.IO) {
        mBlockSet.clear()
        FileUtil.readList(BLOCK_LIST_PATH)?.let { mBlockSet.addAll(it) }
        delay(1000L)
        return@async mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .stream()
            .filter { ApplicationInfo.FLAG_SYSTEM.and(it.flags) != ApplicationInfo.FLAG_SYSTEM }
            .collect(Collectors.toList())
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

        _uploadStatus.value = JOB_STATUS_PENDING
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileUtil.crateIfNotExists(BLOCK_LIST_PATH, FileUtil.PERMISSION_764)
                Files.write(BLOCK_LIST_PATH, mBlockSet)
                ActivityUpdateReceiver.sendNewList(mContext, ArrayList(mBlockSet))
                // update success status when receive response
            } catch (e: Exception) {
                Timber.w(e, "Failed to write block list")
                _uploadStatus.postValue(JOB_STATUS_FAILED)
                _uploadStatus.postValue(null)
            }
        }
        return true
    }

    fun onNewResponse(response: Int) {
        when (response) {
            STATUS_LIST_LOAD_START -> Timber.d("onReceiveSystemServer: Start")

            STATUS_LIST_LOAD_FAILED -> _uploadStatus.value = JOB_STATUS_FAILED

            STATUS_LIST_LOAD_SUCCEED -> _uploadStatus.value = JOB_STATUS_SUCCEED

            else -> throw IllegalArgumentException("WTF response: $response")
        }

        if (response != STATUS_LIST_LOAD_START) {
            _uploadStatus.value = null

            if (BuildConfig.DEBUG) {
                mContext.sendBroadcast(Intent(ActivityUpdateReceiver.ACTION_SERVER_PRINT_LIST))
                val cost = Duration.between(timer, Instant.now()).toMillis()
                Timber.w("${if (response == STATUS_LIST_LOAD_SUCCEED) "Succeed" else "Failed"}: ${cost}ms")
            }
        }
    }

    fun isUploading(): Boolean = _uploadStatus.value == JOB_STATUS_PENDING

    override fun onCleared() {
        super.onCleared()
        mContext.unregisterReceiver(updateStatusReceiver)
        mBlockSet.clear()
    }
}