package me.ranko.autodark.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Objects;

import me.ranko.autodark.R;

public class PermissionLayout extends LinearLayout implements View.OnClickListener {

    private ExpandableLayout mExpandableLayout;
    private CheckedImageView mExpandableButton;

    private final TextView mTitle;
    private final TextView mDescription;

    private boolean isExpanded;

    private MaterialCircleIconView mIcon;

    public PermissionLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PermissionLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PermissionLayout, defStyleAttr, 0);
        setOrientation(VERTICAL);

        View container = LayoutInflater.from(context).inflate(R.layout.widget_permission, this, true);

        mIcon = container.findViewById(R.id.icon);
        mIcon.setImageResource(a.getResourceId(R.styleable.PermissionLayout_srcIcon, android.R.drawable.ic_btn_speak_now));
        if (a.hasValue(R.styleable.PermissionLayout_iconColor)) {
            mIcon.setColorName(Objects.requireNonNull(a.getString(R.styleable.PermissionLayout_iconColor)));
        }

        mTitle = container.findViewById(R.id.title);
        if (a.hasValue(R.styleable.PermissionLayout_title))
            mTitle.setText(a.getText(R.styleable.PermissionLayout_title));

        mExpandableButton = container.findViewById(R.id.button);
        if (a.getBoolean(R.styleable.PermissionLayout_expandable, true)) {
            mExpandableButton.setOnClickListener(this);
        } else {
            mExpandableButton.setVisibility(View.GONE);
        }

        mExpandableLayout = container.findViewById(R.id.expandable);
        if (a.hasValue(R.styleable.PermissionLayout_expandDescription)) {
            isExpanded = a.getBoolean(R.styleable.PermissionLayout_expandDescription, false);
            if (isExpanded ^ mExpandableLayout.isExpanded()) {
                mExpandableLayout.setExpanded(isExpanded, false);
                mExpandableButton.setChecked(isExpanded);
            }
        }

        mDescription = container.findViewById(R.id.description);
        if (a.hasValue(R.styleable.PermissionLayout_description)) {
            mDescription.setText(a.getText(R.styleable.PermissionLayout_description));
        }
        mDescription.setMovementMethod(LinkMovementMethod.getInstance());

        a.recycle();
    }

    public MaterialCircleIconView getTitleIcon() {
        return mIcon;
    }

    public void setIconColor(@NonNull String colorName) {
        mIcon.setColorName(colorName);
    }

    public String getIconColor() {
        return mIcon.getColorName();
    }

    public void setTitle(@StringRes int title) {
        mTitle.setText(title);
    }

    public void setTitle(String title) {
        mTitle.setText(title);
    }

    public String getTitle() {
        return mTitle.getText().toString();
    }

    public void setDescription(@StringRes int description) {
         mDescription.setText(description);
    }

    public void setDescription(String description) {
        mDescription.setText(description);
    }

    public String getDescription() {
        return mDescription.getText().toString();
    }

    @Override
    public void onClick(@Nullable View v) {
        isExpanded = !isExpanded;
        mExpandableButton.setChecked(isExpanded);
        mExpandableLayout.setExpanded(isExpanded);
    }
}
