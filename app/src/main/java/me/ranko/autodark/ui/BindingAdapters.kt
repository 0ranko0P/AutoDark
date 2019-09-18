package me.ranko.autodark.ui

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import me.ranko.autodark.Constant
import me.ranko.autodark.R

@BindingAdapter("setActiveImg")
fun setActiveImg(fab: FloatingActionButton, enabled: Boolean) {
    val icon = if (enabled) R.drawable.ic_on else R.drawable.ic_off
    fab.setImageResource(icon)
}

@BindingAdapter("onButtonSuProgress")
fun onButtonSuProgress(v: TextView, process: Int) {
    v.visibility = if(process == Constant.JOB_STATUS_PENDING) View.GONE else View.VISIBLE
}

@BindingAdapter("onSuProgress")
fun onSuProgress(v: ProgressBar, process: Int) {
    v.visibility = if(process == Constant.JOB_STATUS_PENDING) View.VISIBLE else View.GONE
}