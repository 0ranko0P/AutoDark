package me.ranko.autodark.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.util.Consumer
import androidx.recyclerview.widget.RecyclerView
import com.android.wallpaper.asset.LiveWallpaperThumbAsset
import com.android.wallpaper.model.LiveWallpaperInfo
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.util.ViewPreloadSizeProvider
import me.ranko.autodark.R
import java.util.*
import kotlin.collections.ArrayList

class LiveWallpaperAdapter(private val context: Context,
                           private var mListener: Consumer<LiveWallpaperInfo>,
                           requestManager: RequestManager,
                           private val sizeProvider: ViewPreloadSizeProvider<LiveWallpaperThumbAsset>
) :
        RecyclerView.Adapter<LiveWallpaperAdapter.ViewHolder>(),
        ListPreloader.PreloadModelProvider<LiveWallpaperThumbAsset> {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val mTitleView: TextView = view.findViewById(R.id.title)
        val mImageView: ImageView = view.findViewById<CardView>(R.id.card_container).findViewById(R.id.image)
    }

    private val mRequest = requestManager.asDrawable()
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .error(R.drawable.ic_attention)

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var data: List<LiveWallpaperInfo> = ArrayList(0)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = inflater.inflate(R.layout.grid_item_live_wallpaper, parent, false)
        return ViewHolder(itemView)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.mImageView.setImageDrawable(null)
        holder.mImageView.setOnClickListener(null)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        sizeProvider.setView(holder.mImageView)
        mRequest.load(data[position].getThumbAsset(context))
                .into(holder.mImageView)
        holder.mImageView.tag = position
        holder.mImageView.setOnClickListener { mListener.accept(data[position]) }
        holder.mTitleView.text = data[position].getTitle(context)
    }

    fun setLiveWallpapers(data: List<LiveWallpaperInfo>) {
        this.data = data
        notifyDataSetChanged()
    }

    override fun getPreloadItems(position: Int): MutableList<LiveWallpaperThumbAsset> {
        return Collections.singletonList(data[position].getThumbAsset(context) as LiveWallpaperThumbAsset)
    }

    override fun getPreloadRequestBuilder(item: LiveWallpaperThumbAsset): RequestBuilder<Drawable> {
        return mRequest.load(item)
    }
}