package me.ranko.autodark.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.marcdonald.simplelicensedisplay.SimpleLicenseDisplay
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil

@BindingAdapter("setActiveImg")
fun setActiveImg(fab: FloatingActionButton, status: DarkSwitch) {
    val icon = when (status) {
        DarkSwitch.ON -> R.drawable.ic_on
        DarkSwitch.OFF -> R.drawable.ic_off
        DarkSwitch.SHARE -> R.drawable.ic_send
    }
    fab.setImageResource(icon)
}

@BindingAdapter("onButtonSuProgress")
fun onButtonSuProgress(v: TextView, process: Int) {
    v.visibility = if (process == Constant.JOB_STATUS_PENDING) View.GONE else View.VISIBLE
}

@BindingAdapter("onSuProgress")
fun onSuProgress(v: ProgressBar, process: Int) {
    v.visibility = if (process == Constant.JOB_STATUS_PENDING) View.VISIBLE else View.GONE
}

@BindingAdapter("setAppBarPadding")
fun setAppBarPadding(v: AppBarLayout, viewModel: PermissionViewModel) {
    val statusBar = ViewUtil.getStatusBarHeight(v.resources)
    if (ViewUtil.isLandscape(v.context)) {
        v.setPadding(v.paddingLeft, statusBar / 2, v.paddingRight, statusBar / 2)
    } else {
        v.setPadding(v.paddingLeft, statusBar, v.paddingRight, v.paddingBottom)
    }
}

@BindingAdapter("setLinearViewPadding")
fun setLinearViewPadding(v: LinearLayout, viewModel: PermissionViewModel) {
    val top = v.paddingTop + ViewUtil.getStatusBarHeight(v.resources)
    v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
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