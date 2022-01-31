package me.ranko.autodark.ui.widget;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.TimeUtils.TimeTicker;

import java.time.LocalTime;
import java.util.Objects;

import me.ranko.autodark.BuildConfig;
import me.ranko.autodark.R;
import me.ranko.autodark.Utils.DarkTimeUtil;
import me.ranko.autodark.Utils.ViewUtil;
import me.ranko.autodark.ui.ManagerAppListAdapter;

public final class XposedManagerView implements DefaultLifecycleObserver {

    private final View root;
    private TextView time;

    private final RecyclerView recyclerView;
    private ManagerAppListAdapter adapter;

    private Activity mActivity;

    private TimeTicker ticker;

    @SuppressLint("InflateParams")
    public XposedManagerView(Activity context, ViewGroup container) {
        mActivity = context;
        root = LayoutInflater.from(context).inflate(R.layout.widget_xposed_manager, container, false);
        recyclerView = root.findViewById(R.id.recyclerView);
        adapter = new ManagerAppListAdapter(context);
        adaptManagerView();

        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Display defaultDisplay = windowManager.getDefaultDisplay();
        Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(defaultDisplay);
        View containerRoot = container.getRootView();
        containerRoot.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                containerRoot.removeOnLayoutChangeListener(this);
                int containerHeight = container.getMeasuredHeight();
                int containerWidth = container.getMeasuredWidth();
                float outScale = screenSize.x / (float) containerWidth;
                int scaledHeight = Math.round(containerHeight * outScale);
                root.measure(
                        makeMeasureSpec(screenSize.x, EXACTLY),
                        makeMeasureSpec(scaledHeight, EXACTLY));
                root.layout(0, 0, screenSize.x, scaledHeight);

                float scale = (float) containerWidth / screenSize.x;
                root.setScaleX(scale);
                root.setScaleY(scale);
                root.setPivotX(0f);
                root.setPivotY(0f);
                container.addView(root, root.getMeasuredWidth(), root.getMeasuredHeight());
                recyclerView.setAdapter(adapter);
            }
        });
        ((LifecycleOwner) mActivity).getLifecycle().addObserver(this);
    }

    private void adaptManagerView() {
        View statusBar = root.findViewById(R.id.statusBar);
        View toolbar = statusBar.findViewById(R.id.toolbar);
        time = statusBar.findViewById(R.id.lock_time);
        TextView id = toolbar.findViewById(R.id.id);
        id.setText(BuildConfig.APPLICATION_ID);

        ImageView navIcon = toolbar.findViewById(R.id.navIcon);
        ImageView menu = toolbar.findViewById(R.id.menu);
        navIcon.setImageDrawable(getDrawable(mActivity, R.drawable.ic_arrow_back));
        menu.setImageDrawable(getDrawable(mActivity, R.drawable.ic_more));
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        ticker = TimeTicker.registerNewReceiver(mActivity, this::updateTime);
        updateTime();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        mActivity.unregisterReceiver(ticker);
        ticker = null;
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        destroy();
    }

    private void updateTime() {
        String nowTime = DarkTimeUtil.getDisplayFormattedString(LocalTime.now());
        time.setText(nowTime);
    }

    private static Drawable getDrawable(Context context, @DrawableRes int res) {
        int color = ViewUtil.INSTANCE.getAttrColor(context, R.attr.colorOnSurface);
        Drawable drawable = Objects.requireNonNull(ContextCompat.getDrawable(context, res));
        drawable.setTint(color);
        drawable.setTintMode(PorterDuff.Mode.SRC_IN);
        return drawable;
    }

    public void destroy() {
        if (ticker != null) {
            mActivity.unregisterReceiver(ticker);
            ((LifecycleOwner) mActivity).getLifecycle().removeObserver(this);
        }
        ticker = null;
        mActivity = null;

        // destroy static DUMMY_ICON in adapter
        recyclerView.setAdapter(null);
        adapter = null;
    }
}