package me.ranko.autodark.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import androidx.core.content.ContextCompat
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ranko.autodark.Constant.COMMAND_GRANT_ADB
import me.ranko.autodark.Constant.COMMAND_GRANT_PM
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ShellJobUtil
import me.ranko.autodark.core.LoadStatus
import me.ranko.autodark.core.ShizukuApi
import timber.log.Timber

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Progress that indicates grant with root permission job status
     *
     * @see LoadStatus
     * */
    val sudoJobStatus = ObservableInt()
    val adbJobStatus = ObservableInt()
    val shizukuJobStatus = ObservableInt()

    private val _permissionResult = MutableLiveData<Boolean>()
    val permissionResult: LiveData<Boolean>
        get() = _permissionResult

    fun onAdbCheck() = grantPermission(adbJobStatus)

    fun grantWithRoot() = grantPermission(sudoJobStatus)

    fun grantWithShizuku() = grantPermission(shizukuJobStatus)

    /**
     * Procedure to grant secure permission
     * */
    private fun grantPermission(jobIndicator: ObservableInt) = viewModelScope.launch {
        try {
            jobIndicator.set(LoadStatus.START)
            when (jobIndicator) {
                adbJobStatus -> { /* do nothing */ }

                sudoJobStatus -> ShellJobUtil.runSudoJob(COMMAND_GRANT_PM)

                shizukuJobStatus -> ShizukuApi.grantWithShizuku()
            }
        } catch (e: Exception) {
            Timber.i(e)
        } finally {
            delay(800L) // Show progress longer

            // Notify permission result
            _permissionResult.value = checkSecurePermission(getApplication())
            // Notify job completed
            jobIndicator.set(if (_permissionResult.value!!) LoadStatus.SUCCEED else LoadStatus.FAILED)
        }
    }

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PermissionViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return PermissionViewModel(application) as T
                }
                throw IllegalArgumentException("Unable to construct viewModel")
            }
        }

        fun checkSecurePermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED

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