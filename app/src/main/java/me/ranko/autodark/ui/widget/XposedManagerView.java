package me.ranko.autodark.ui.widget;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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

import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.TimeUtils.TimeTicker;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.ranko.autodark.BuildConfig;
import me.ranko.autodark.Constant;
import me.ranko.autodark.R;
import me.ranko.autodark.Utils.DarkTimeUtil;
import me.ranko.autodark.Utils.ViewUtil;
import me.ranko.autodark.ui.Manager;
import me.ranko.autodark.ui.ManagerAppListAdapter;
import me.ranko.autodark.ui.ManagerAppListAdapter.DummyApplicationInfo;
import me.ranko.autodark.ui.ManagerAppListAdapter.ImeApplicationInfo;
import timber.log.Timber;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

public final class XposedManagerView implements DefaultLifecycleObserver {

    private final View root;
    private TextView time;

    private final RecyclerView recyclerView;
    private ManagerAppListAdapter adapter;

    public final Manager type;

    private Activity mActivity;

    private TimeTicker ticker;

    @SuppressLint("InflateParams")
    public XposedManagerView(Activity context, ViewGroup container, Boolean ime) {
        mActivity = context;
        root = LayoutInflater.from(context).inflate(R.layout.widget_xposed_manager, null);
        recyclerView = root.findViewById(R.id.recyclerView);

        PackageManager pkgManager = context.getPackageManager();
        type = getManagerType(pkgManager);
        adaptManagerView();
        adapter = new ManagerAppListAdapter(context, buildAppList(context, pkgManager, ime), type);

        Application app = context.getApplication();
        WindowManager windowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);

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

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private void adaptManagerView() {
        View statusBar = root.findViewById(R.id.statusBar);
        View toolbar = statusBar.findViewById(R.id.toolbar);
        time = statusBar.findViewById(R.id.lock_time);
        TextView name = toolbar.findViewById(R.id.name);
        TextView id = toolbar.findViewById(R.id.id);
        name.setText(mActivity.getString(R.string.app_name));
        id.setText(BuildConfig.APPLICATION_ID);

        View preference = root.findViewById(R.id.preferenceContainer);
        TextView preferenceTitle = preference.findViewById(R.id.preferenceTitle);
        @DimenRes int preferencePadding;

        if (type == Manager.LSPosed) {
            preferencePadding = R.dimen.preference_lsp_padding;
            time.setTextColor(name.getTextColors());
            preferenceTitle.setText(R.string.enable_scope_lsp);
            id.setTextColor(mActivity.getColor(R.color.text_color_xposed_list));
            statusBar.setBackgroundColor(mActivity.getColor(R.color.bottom_sheet_background));
        } else {
            preferencePadding = R.dimen.preference_edx_padding;
            preferenceTitle.setText(R.string.enable_scope_edx);
            int white = mActivity.getColor(R.color.material_white_1000);
            name.setTextColor(white);
            id.setTextColor(white);
            statusBar.setBackgroundColor(mActivity.getColor(R.color.edXposedAccent));
        }

        preference.setPadding(mActivity.getResources().getDimensionPixelSize(preferencePadding),
                preference.getPaddingTop(),
                preference.getPaddingEnd(),
                preference.getPaddingBottom());

        ImageView navIcon = toolbar.findViewById(R.id.navIcon);
        ImageView menu = toolbar.findViewById(R.id.menu);
        navIcon.setImageDrawable(getDrawable(mActivity, type, R.drawable.ic_arrow_back));
        menu.setImageDrawable(getDrawable(mActivity, type, R.drawable.ic_more));
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

    private void updateTime() {
        String nowTime = DarkTimeUtil.getDisplayFormattedString(LocalTime.now());
        time.setText(nowTime);
    }

    private static Drawable getDrawable(Context context, Manager manager, @DrawableRes int res) {
        int color = (manager == Manager.LSPosed) ?
                ViewUtil.INSTANCE.getAttrColor(context, R.attr.colorOnSurface) :
                context.getColor(R.color.material_white_1000);
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

    private static List<ApplicationInfo> buildAppList(Context context, PackageManager manager, boolean ime) {
        String dummyName = context.getString(R.string.other_app);
        int maxApp = 8;
        ArrayList<ApplicationInfo> result = new ArrayList<>(maxApp);
        ApplicationInfo dummy = new ManagerAppListAdapter.DummyApplicationInfo(dummyName);
        ApplicationInfo system;
        try {
            system = manager.getApplicationInfo(Constant.ANDROID_PACKAGE, PackageManager.MATCH_SYSTEM_ONLY);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.i(e);
            system = new DummyApplicationInfo(dummyName);
            system.name = "System";
            system.packageName = Constant.ANDROID_PACKAGE;
        }
        result.add(system);

        if (ime) {
            ApplicationInfo imeApp = new ImeApplicationInfo(context.getString(R.string.inputmethod));
            result.add(imeApp);
            result.add(new ImeApplicationInfo(imeApp.name + '2'));
        }

        for (int i = 0, size = maxApp - result.size(); i < size; i++) {
            result.add(dummy);
        }
        return result;
    }

    public static Manager getManagerType(PackageManager pkgManager) {
        try {
            pkgManager.getPackageInfo(Manager.EDXposed.getPkg(), PackageManager.GET_PERMISSIONS);
            return Manager.EDXposed;
        } catch (PackageManager.NameNotFoundException ignored) {
            return Manager.LSPosed;
        }
    }
}