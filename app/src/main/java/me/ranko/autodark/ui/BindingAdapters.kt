package me.ranko.autodark.ui

import android.graphics.drawable.Animatable2
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.marcdonald.simplelicensedisplay.SimpleLicenseDisplay
import me.ranko.autodark.R
import me.ranko.autodark.core.LoadStatus

@BindingAdapter("setActiveImg")
fun setActiveImg(fab: FloatingActionButton, state: DarkSwitch) {
    val lastState = fab.getTag()
    val iconRes = if (lastState == null) {
        when (state) {
            DarkSwitch.ON -> R.drawable.ic_on
            DarkSwitch.OFF -> R.drawable.ic_off
            DarkSwitch.SHARE -> R.drawable.ic_send
        }
    } else {
        val nextStatus = (lastState as DarkSwitch).id - state.id
        when (nextStatus) {
            DarkSwitch.ON.id - DarkSwitch.OFF.id -> R.drawable.ic_on_to_off_anim
            DarkSwitch.OFF.id - DarkSwitch.ON.id -> R.drawable.ic_off_to_on_anim
            DarkSwitch.ON.id - DarkSwitch.SHARE.id -> R.drawable.ic_on_to_share_anim
            DarkSwitch.OFF.id - DarkSwitch.SHARE.id -> R.drawable.ic_off_to_share_anim
            DarkSwitch.SHARE.id - DarkSwitch.ON.id -> R.drawable.ic_share_to_on_anim
            DarkSwitch.SHARE.id - DarkSwitch.OFF.id -> R.drawable.ic_share_to_off_anim
            else -> throw RuntimeException("Unknown status: $nextStatus, $state")
        }
    }

    fab.setImageResource(iconRes)
    fab.tag = state
    if (lastState != null) {
        (fab.drawable as Animatable2).start()
    }
}

@BindingAdapter("onButtonJobProgress")
fun onButtonJobProgress(v: TextView, @LoadStatus process: Int) {
    v.visibility = if (process == LoadStatus.START) View.GONE else View.VISIBLE
}

@BindingAdapter("onJobProgress")
fun onJobProgress(v: ProgressBar, @LoadStatus process: Int) {
    v.visibility = if (process == LoadStatus.START) View.VISIBLE else View.GONE
}

@BindingAdapter("bindLicense")
fun setLicense(v: SimpleLicenseDisplay, license: License) {
    val licenseView = v.findViewById<TextView>(com.marcdonald.simplelicensedisplay.R.id.txt_license_license)
    licenseView.text = license.license
    licenseView.visibility = View.VISIBLE

    val titleView = v.findViewById<TextView>(com.marcdonald.simplelicensedisplay.R.id.txt_license_title)
    titleView.text = license.name
    titleView.visibility = View.VISIBLE
}