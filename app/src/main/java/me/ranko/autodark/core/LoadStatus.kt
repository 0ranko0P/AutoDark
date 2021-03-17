package me.ranko.autodark.core

import androidx.annotation.IntDef
import me.ranko.autodark.core.LoadStatus.Companion.FAILED
import me.ranko.autodark.core.LoadStatus.Companion.START
import me.ranko.autodark.core.LoadStatus.Companion.SUCCEED

@IntDef(START, SUCCEED, FAILED)
@Retention(AnnotationRetention.SOURCE)
annotation class LoadStatus {
    companion object {
        const val START = 0x001A
        const val FAILED = START.shl(1)
        const val SUCCEED = FAILED.shl(1)
    }
}