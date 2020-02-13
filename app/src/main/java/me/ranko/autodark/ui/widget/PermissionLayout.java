package me.ranko.autodark.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.ranko.autodark.R;

public class PermissionLayout extends LinearLayout implements View.OnClickListener {

    private ExpandableLayout mExpandableLayout;
    private CheckedImageView mExpandableButton;

    private boolean isExpanded;

    private ImageView mIcon;

    public PermissionLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PermissionLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PermissionLayout, defStyleAttr, 0);

        LayoutInflater.from(context).inflate(R.layout.widget_permission, this, true);

        mIcon = prepareTitleIcon(context, findViewById(R.id.titleRoot), a);

        TextView mTitle = findViewById(R.id.title);
        if (a.hasValue(R.styleable.PermissionLayout_title))
            mTitle.setText(a.getText(R.styleable.PermissionLayout_title));

        mExpandableButton = findViewById(R.id.button);
        if (a.getBoolean(R.styleable.PermissionLayout_expandable, true)) {
            mExpandableButton.setOnClickListener(this);
        } else {
            mExpandableButton.setVisibility(View.GONE);
        }

        mExpandableLayout = findViewById(R.id.expandable);
        if (a.hasValue(R.styleable.PermissionLayout_expandDescription)) {
            this.isExpanded = !a.getBoolean(R.styleable.PermissionLayout_expandDescription, false);
            onClick(null);
        }

        TextView mDescription = findViewById(R.id.description);
        if (a.hasValue(R.styleable.PermissionLayout_description)) {
            mDescription.setText(a.getText(R.styleable.PermissionLayout_description));
        }

        a.recycle();
    }

    private ImageView prepareTitleIcon(Context context, ViewGroup root, TypedArray a) {
        ImageView icon = root.findViewById(R.id.icon);
        boolean notMaterial = a.getBoolean(R.styleable.PermissionLayout_normalIcon, false);
        if (notMaterial) {
            ViewGroup.LayoutParams params = icon.getLayoutParams();
            root.removeView(icon);

            icon = new ImageView(context, null, 0);
            icon.setLayoutParams(params);
            root.addView(icon, 0);
        }

        if (!notMaterial && a.hasValue(R.styleable.PermissionLayout_iconColor)) {
            ((MaterialCircleIconView) icon).setColorName(a.getString(R.styleable.PermissionLayout_iconColor));
        }

        icon.setImageResource(a.getResourceId(R.styleable.PermissionLayout_src, android.R.drawable.ic_btn_speak_now));
        return icon;
    }

    public ImageView getTitleIcon() {
        return mIcon;
    }

    @Override
    public void onClick(@Nullable View v) {
        isExpanded = !isExpanded;
        mExpandableButton.setChecked(isExpanded);
        mExpandableLayout.setExpanded(isExpanded);
    }
}
