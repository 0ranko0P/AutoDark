package me.ranko.autodark.ui.widget;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.Lifecycle;

import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.widget.LockScreenPreviewer;

import me.ranko.autodark.R;

/**
 * Layout includes two CardView that displays Home & LockScreen wallpaper
 * */
public class WallpaperPreviewLayout extends FrameLayout {

    private ImageView mImgHome;
    private ImageView mImgLock;

    private CardView mCardHome;
    private CardView mCardLock;

    private LockScreenPreviewer mLockScreenPreviewer;
    private CardView fadeCover;

    private final float mScreenAspectRatio;
    private int dividerHorizontal;
    private int marginHorizontal;

    public WallpaperPreviewLayout(Context context) {
        this(context, null);
    }

    public WallpaperPreviewLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WallpaperPreviewLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View rootView = LayoutInflater.from(context).inflate(R.layout.preview_wallpaper, this, true);
        mCardHome = rootView.findViewById(R.id.preview_card_home);
        mCardLock = rootView.findViewById(R.id.preview_card_lock);
        mImgHome = mCardHome.findViewById(R.id.card_preview_image);
        mImgLock = mCardLock.findViewById(R.id.card_preview_image);

        // adjust margin for card view
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.WallpaperPreviewLayout, defStyleAttr, 0);
        dividerHorizontal = a.getDimensionPixelSize(R.styleable.WallpaperPreviewLayout_preview_dividerHorizontal, getResources().getDimensionPixelSize(R.dimen.preview_card_divider));
        int marginH = a.getDimensionPixelSize(R.styleable.WallpaperPreviewLayout_preview_marginHorizontal, getResources().getDimensionPixelSize(R.dimen.preview_card_margin_horizontal));
        int marginV = a.getDimensionPixelSize(R.styleable.WallpaperPreviewLayout_preview_marginVertical, getResources().getDimensionPixelSize(R.dimen.preview_card_margin_vertical));
        a.recycle();
        setMargin(marginH, marginV, dividerHorizontal);

        mLockScreenPreviewer = new LockScreenPreviewer(context, mCardLock);

        // fadeCover view for ViewPager
        fadeCover = new CardView(context, null, R.style.PreviewCard);
        fadeCover.setId(R.id.fade_cover);
        fadeCover.setAlpha(0f);
        fadeCover.setElevation(0f);
        fadeCover.setBackgroundColor(getResources().getColor(R.color.preview_pager_background, context.getTheme()));
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(marginH, marginV, marginH, marginV);
        addView(fadeCover, params);

        mScreenAspectRatio = ScreenSizeCalculator.getInstance().getScreenAspectRatio(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        int previewWidth = (availableWidth - marginHorizontal) / 2;
        int previewHeight = (int) (previewWidth * mScreenAspectRatio);

        adjustChildView(mCardHome, previewWidth, previewHeight);
        adjustChildView(mCardLock, previewWidth, previewHeight);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void adjustChildView(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    public void setMargin(int marginH, int marginV, int dividerHorizontal) {
        this.dividerHorizontal = dividerHorizontal;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mCardHome.getLayoutParams();
        params.setMargins(marginH, marginV, 0, marginV);
        mCardHome.setLayoutParams(params);
        params = (LinearLayout.LayoutParams) mCardLock.getLayoutParams();
        params.setMargins(dividerHorizontal, marginV, marginH, marginV);
        mCardLock.setLayoutParams(params);
        marginHorizontal = marginH + marginH + dividerHorizontal;
    }

    public void attachLifecycle(Lifecycle lifecycle, Activity activity) {
        mLockScreenPreviewer.attachLifeCycle(lifecycle, activity);
    }

    public void setHomeWallpaper(@Nullable Drawable home) {
        mImgHome.setImageDrawable(home);
    }

    public void setLockWallpaper(@Nullable Drawable lock) {
        mImgLock.setImageDrawable(lock);
    }

    public ImageView getHomeView() {
        return mImgHome;
    }

    public ImageView getLockView() {
        return mImgLock;
    }

    public LockScreenPreviewer getLockScreenPreviewer() {
        return mLockScreenPreviewer;
    }
}
