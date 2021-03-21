package me.ranko.autodark.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Animatable2
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.ranko.autodark.R
import me.ranko.autodark.Utils.CircularAnimationUtil

class BlockListAdapter(private val viewModel: BlockListViewModel) : RecyclerView.Adapter<BlockListAdapter.Companion.ViewHolder>(), View.OnClickListener {
    private var data: List<ApplicationInfo> = emptyList()

    private var isSearchMode = false

    private val rippleAnimDuration =
        viewModel.getApplication<Application>().resources.getInteger(android.R.integer.config_shortAnimTime)
            .toLong()

    companion object {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val rootRipple: View = view.findViewById(R.id.appRootBg)
            val rootView: RelativeLayout = view.findViewById(R.id.appRoot)
            val icon: ImageView = rootView.findViewById(R.id.icon)
            val indicator: ImageView = rootView.findViewById(R.id.indicator)
            val name: TextView = rootView.findViewById(R.id.name)
            val id: TextView = rootView.findViewById(R.id.appID)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_block_list, parent, false)
    )

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        setData(emptyList())
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.icon.tag = null
        holder.rootView.tag = null
        holder.rootView.setOnClickListener(null)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = data[position]
        applyBlockedMark(viewModel.isBlocked(app.packageName), holder, false)
        holder.name.text = viewModel.getAppName(app)
        holder.id.text = app.packageName
        holder.rootView.setOnClickListener(this)
        holder.rootView.tag = holder

        if (!isSearchMode) {
            val mAnimation = AnimationUtils.loadAnimation(viewModel.getApplication(), R.anim.item_shift_vertical)
            holder.rootView.startAnimation(mAnimation)
        }

        holder.icon.tag = app.packageName
        val iconDrawable = viewModel.getAppIcon(app)
        val pkg = holder.id.text.toString()
        if (pkg == app.packageName) {
            holder.icon.setImageDrawable(iconDrawable)
            if (!isSearchMode) {
                val alpha = AlphaAnimation(0.0f, 1.0f)
                alpha.duration = 300L
                holder.icon.startAnimation(alpha)
            }
        }
    }

    override fun getItemCount() = data.size

    fun setData(data: List<ApplicationInfo>) {
        this.data = data
        notifyDataSetChanged()
    }

    fun setSearchMode(isSearchMode: Boolean) {
        this.isSearchMode = isSearchMode
    }

    override fun onClick(v: View) {
        if (viewModel.isRefreshing.value == true) return
        val holder = v.tag as ViewHolder
        val position = holder.adapterPosition
        val isBlocked = viewModel.onAppSelected(data[position])
        applyBlockedMark(isBlocked, holder)
    }

    private fun applyBlockedMark(isBlocked: Boolean, holder: ViewHolder, animate: Boolean = true) {
        val visibility = if (isBlocked) {
            if (animate) {
                (holder.indicator.drawable as Animatable2).start()
                val animator = CircularAnimationUtil.buildAnimator(holder.rootRipple, holder.rootRipple, 256.0f)
                animator.interpolator = AccelerateInterpolator()
                animator.duration = rippleAnimDuration
                animator.start()
            }
            View.VISIBLE
        } else {
            if (animate) {
                val alphaAnim = AlphaAnimation(0.6f, 0.0f)
                alphaAnim.duration = rippleAnimDuration
                alphaAnim.interpolator = AccelerateDecelerateInterpolator()
                holder.rootRipple.startAnimation(alphaAnim)
                SystemClock.sleep(160L)
            }
            View.INVISIBLE
        }
        holder.rootRipple.visibility = visibility
        holder.indicator.visibility = visibility
    }
}