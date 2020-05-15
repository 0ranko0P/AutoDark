package me.ranko.autodark.ui

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Animatable2
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.ranko.autodark.R

class BlockListAdapter(private val viewModel: BlockListViewModel) : RecyclerView.Adapter<BlockListAdapter.ViewHolder>(), View.OnClickListener {
    private var data: List<ApplicationInfo>? = null

    companion object
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rootView: RelativeLayout = view.findViewById(R.id.appRoot)
        val icon: ImageView = view.findViewById(R.id.icon)
        val indicator: ImageView = view.findViewById(R.id.indicator)
        val name: TextView = view.findViewById(R.id.name)
        val id: TextView = view.findViewById(R.id.appID)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_block_list, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = data!![position]
        holder.icon.setImageDrawable(viewModel.getAppIcon(app))
        applyBlockedMark(viewModel.isBlocked(app.packageName), holder.indicator, false)

        holder.name.text = viewModel.getAppName(app)
        holder.id.text = app.packageName
        holder.rootView.setOnClickListener(this)
        holder.rootView.tag = holder

        val mAnimation = AnimationUtils.loadAnimation(viewModel.getApplication(), R.anim.item_shift_vertical)
        val alpha = AlphaAnimation(0.0f, 1.0f)
        alpha.duration = 300L
        holder.icon.startAnimation(alpha)
        holder.rootView.startAnimation(mAnimation)
    }

    override fun getItemCount() = data?.size ?: 0

    fun setData(data: List<ApplicationInfo>) {
        this.data = data
        notifyDataSetChanged()
    }

    override fun onClick(v: View) {
        val holder = v.tag as ViewHolder
        val position = holder.adapterPosition
        val isBlocked = viewModel.onAppSelected(data!![position])
        applyBlockedMark(isBlocked, holder.indicator)
    }

    private fun applyBlockedMark(isBlocked: Boolean, indicator: ImageView, animate: Boolean = true) {
        if (isBlocked) {
            indicator.visibility = View.VISIBLE
            if (animate) (indicator.drawable as Animatable2).start()
        } else {
            indicator.visibility = View.INVISIBLE
        }
    }
}