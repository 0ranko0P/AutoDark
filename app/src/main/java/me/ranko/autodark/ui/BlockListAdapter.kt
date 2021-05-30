package me.ranko.autodark.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Animatable2
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
import androidx.collection.ArraySet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.coroutines.*
import me.ranko.autodark.R
import me.ranko.autodark.Utils.CircularAnimationUtil
import me.ranko.autodark.model.Blockable
import me.ranko.autodark.model.BlockableApplication

class BlockListAdapter(context: Context,
                       requestManager: RequestManager,
                       private val listener: AppSelectListener) :
    RecyclerView.Adapter<BlockListAdapter.BaseViewHolder<Any>>() {

    interface AppSelectListener {
        fun onAppBlockStateChanged(app: Blockable): Boolean

        fun isAppBlocked(app: Blockable): Boolean

        fun onEditItemClicked(app: Blockable)
    }

    companion object {
        private fun <T> randomAccess(data:Collection<T>, position: Int): T {
            return when (data) {
                is List -> data[position]

                is ArraySet -> data.valueAt(position)!!

                else -> throw UnsupportedOperationException("Not a random accessible collection: " + data::class)
            }
        }
    }

    private class AppListDiffCallback(
        private val oldData: Collection<Blockable>,
        private val newData: Collection<Blockable>,
    ): DiffUtil.Callback() {

        private lateinit var old:Blockable
        private lateinit var new:Blockable

        override fun getOldListSize(): Int = oldData.size

        override fun getNewListSize(): Int = newData.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            old = randomAccess(oldData, oldItemPosition)
            new = randomAccess(newData, newItemPosition)
            return old::class.java == new::class.java && old.getPackageName() == new.getPackageName()
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old.isPrimaryUser() == new.isPrimaryUser()
        }
    }

    private val packageManager = context.packageManager

    private var data: Collection<Blockable> = emptyList()

    private var mDiffJob: Job? = null

    private var isSearchMode = false
    private var isRefreshing = false

    private val rippleAnimDuration =
        context.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

    private val mRequest = requestManager
            .asDrawable()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .transition(DrawableTransitionOptions.withCrossFade())
            .error(R.drawable.ic_attention)

    abstract class BaseViewHolder<T>(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(data: T, listener: AppSelectListener)

        abstract fun recycle()
    }

    class MiniViewHolder(view: View, private val mListener: AppSelectListener) : BaseViewHolder<Blockable>(view) {
        private val id: TextView = view.findViewById(R.id.appID)

        override fun bind(data: Blockable, listener: AppSelectListener) {
            id.text = data.getPackageName()
            itemView.setOnClickListener { mListener.onEditItemClicked(data) }
        }

        override fun recycle() = itemView.setOnClickListener(null)
    }

    inner class AppViewHolder(view: View) : BaseViewHolder<BlockableApplication>(view) {
        private val rootRipple: View = view.findViewById(R.id.appRootBg)
        private val rootView: RelativeLayout = view.findViewById(R.id.appRoot)
        private val icon: ImageView = rootView.findViewById(R.id.icon)
        private val indicator: ImageView = rootView.findViewById(R.id.indicator)
        private val name: TextView = rootView.findViewById(R.id.name)
        private val id: TextView = rootView.findViewById(R.id.appID)

        override fun bind(data: BlockableApplication, listener: AppSelectListener) {
            applyBlockedMark(listener.isAppBlocked(data), false)
            rootView.setOnClickListener {
                if (!isRefreshing) {
                    val isBlocked = listener.onAppBlockStateChanged(data)
                    applyBlockedMark(isBlocked, true)
                }
            }
            mRequest.load(data).into(icon)
            id.text = data.packageName
            name.text = if (data.isPrimaryUser()) {
                data.loadLabel(packageManager)
            } else {
                id.context.getString(R.string.app_badged_label, data.loadLabel(packageManager), data.userId)
            }

            if (!isSearchMode) {
                val mAnimation = AnimationUtils.loadAnimation(rootView.context, R.anim.item_shift_vertical)
                rootView.startAnimation(mAnimation)
            }
        }

        private fun applyBlockedMark(isBlocked: Boolean, animate: Boolean) {
            val visibility = if (isBlocked) {
                if (animate) {
                    (indicator.drawable as Animatable2).start()
                    val animator = CircularAnimationUtil.buildAnimator(rootRipple, rootRipple, 256.0f)
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
                    rootRipple.startAnimation(alphaAnim)
                }
                View.INVISIBLE
            }

            rootRipple.visibility = visibility
            indicator.visibility = visibility
        }

        override fun recycle() = rootView.setOnClickListener(null)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<Any> {
        return if (viewType == 0) {
            AppViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_block_list, parent, false))
        } else {
            MiniViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_block_list_minimum, parent, false), listener)
        } as BaseViewHolder<Any>
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        submitData(emptyList())
    }

    override fun onBindViewHolder(holder: BaseViewHolder<Any>, position: Int) {
        holder.bind(randomAccess(data, position), listener)
    }

    override fun getItemCount() = data.size

    override fun getItemViewType(position: Int): Int {
        return if (randomAccess(data, position) is ApplicationInfo) 0 else 1
    }

    fun submitData(data: Collection<Blockable>) {
        mDiffJob?.let { job -> if (job.isActive) job.cancel() }

        if (this.data.isEmpty() || data.isEmpty() || !isSearchMode) {
            this.data = data
            notifyDataSetChanged()
        } else {
            val start = System.currentTimeMillis()
            mDiffJob = CoroutineScope(Dispatchers.IO).launch {
                val result = DiffUtil.calculateDiff(AppListDiffCallback(this@BlockListAdapter.data, data))
                // do not update list too frequent
                if (System.currentTimeMillis() - start < 200L) {
                    delay(300L)
                } else if (!isActive) {
                    return@launch // drop outdated result
                }

                withContext(Dispatchers.Main) {
                    this@BlockListAdapter.data = data
                    result.dispatchUpdatesTo(this@BlockListAdapter)
                }
            }
        }
    }

    fun setSearchMode(isSearchMode: Boolean) {
        this.isSearchMode = isSearchMode
    }

    fun setRefreshing(isRefreshing: Boolean) {
        this.isRefreshing = isRefreshing
    }
}