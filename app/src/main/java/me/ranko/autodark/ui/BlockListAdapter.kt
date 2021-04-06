package me.ranko.autodark.ui

import android.content.Context
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
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import me.ranko.autodark.R
import me.ranko.autodark.Utils.CircularAnimationUtil

class BlockListAdapter(context: Context,
                       requestManager: RequestManager,
                       private val listener: AppSelectListener) :
    RecyclerView.Adapter<BlockListAdapter.Companion.ViewHolder>(), View.OnClickListener {

    interface AppSelectListener {
        fun onAppSelected(app: ApplicationInfo): Boolean

        fun isAppSelected(app: ApplicationInfo): Boolean
    }

    private val packageManager = context.packageManager

    private var data: List<ApplicationInfo> = emptyList()

    private var isSearchMode = false
    private var isRefreshing = false

    private val rippleAnimDuration =
        context.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

    private val mRequest = requestManager
            .asDrawable()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .transition(DrawableTransitionOptions.withCrossFade())
            .error(R.drawable.ic_attention)

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
        holder.rootView.tag = null
        holder.rootView.setOnClickListener(null)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = data[position]
        applyBlockedMark(listener.isAppSelected(app), holder, false)
        mRequest.load(app).into(holder.icon)

        holder.name.text = app.loadLabel(packageManager)
        holder.id.text = app.packageName
        holder.rootView.setOnClickListener(this)
        holder.rootView.tag = holder

        if (!isSearchMode) {
            val mAnimation = AnimationUtils.loadAnimation(holder.rootView.context, R.anim.item_shift_vertical)
            holder.rootView.startAnimation(mAnimation)
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

    fun setRefreshing(isRefreshing: Boolean) {
        this.isRefreshing = isRefreshing
    }

    override fun onClick(v: View) {
        if (isRefreshing) return

        val holder = v.tag as ViewHolder
        val position = holder.adapterPosition
        val isBlocked = listener.onAppSelected(data[position])
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