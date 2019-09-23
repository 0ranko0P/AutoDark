package me.ranko.autodark.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import kotlinx.coroutines.*
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ShellJobUtil
import timber.log.Timber

fun AndroidViewModel.checkPermissionGranted(): Boolean {
    val context = getApplication<Application>()
    val permission = context.checkCallingOrSelfPermission(PERMISSION_WRITE_SECURE_SETTINGS)
    return PackageManager.PERMISSION_GRANTED == permission
}

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val sudoJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main.plus(sudoJob))

    /**
     * Progress that indicates grant with root permission job status
     *
     * @see  JOB_STATUS_SUCCEED
     * @see  JOB_STATUS_FAILED
     * @see  JOB_STATUS_PENDING
     * */
    val sudoJobStatus = ObservableInt()

    private val _permissionResult = MutableLiveData<Boolean>()
    val permissionResult: LiveData<Boolean>
        get() = _permissionResult

    fun onAdbCheck() {
        _permissionResult.value = checkPermissionGranted()
    }

    fun grantWithRoot() = uiScope.launch {
        sudoJobStatus.set(JOB_STATUS_PENDING)

        delay(800L) // Show progress longer

        val isRooted: Boolean = try {
            ShellJobUtil.runSudoJob(COMMAND_GRANT_ROOT)
            true
        } catch (e: Exception) {
            false
        }

        // Notify job completed
        sudoJobStatus.set(if (isRooted) JOB_STATUS_SUCCEED else JOB_STATUS_FAILED)

        // dismiss dialog if rooted
        _permissionResult.value = isRooted
        Timber.d("Root job finished, result: %s", isRooted)
    }

    /**
     * @return  System status bar height
     *
     * @link    https://stackoverflow.com/questions/3407256/height-of-status-bar-in-android/47125610#47125610
     * */
    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
           return context.resources.getDimensionPixelSize(resourceId)
        }
        return 1
    }

    override fun onCleared() {
        super.onCleared()
        sudoJob.cancel()
    }

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PermissionViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return PermissionViewModel(application) as T
                }
                throw IllegalArgumentException("Unable to construct viewModel")
            }
        }

        @JvmStatic
        val copyAdbCommand = View.OnClickListener { v ->
            val clipboard =
                v.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("command", COMMAND_GRANT_ADB)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(v.context, R.string.app_copy_adb, Toast.LENGTH_SHORT).show()
        }

        @JvmStatic
        val shareAdbCommand = View.OnClickListener { v ->
            val sharingIntent = Intent(Intent.ACTION_SEND)
            sharingIntent.type = "text/plain"
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Null")
            sharingIntent.putExtra(Intent.EXTRA_TEXT, COMMAND_GRANT_ADB)
            v.context.startActivity(
                Intent.createChooser(sharingIntent, v.resources.getString(R.string.adb_share_text))
            )
        }
    }
}