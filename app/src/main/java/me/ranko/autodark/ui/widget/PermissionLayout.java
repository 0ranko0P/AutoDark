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

import java.util.Objects;

import me.ranko.autodark.R;

public class PermissionLayout extends LinearLayout implements View.OnClickListener {

    private ExpandableLayout mExpandableLayout;
    private CheckedImageView mExpandableButton;

    private boolean isExpanded;

    private MaterialCircleIconView mIcon;

    public PermissionLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PermissionLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PermissionLayout, defStyleAttr, 0);

        LayoutInflater.from(context).inflate(R.layout.widget_permission, this, true);

        mIcon = findViewById(R.id.icon);
        mIcon.setImageResource(a.getResourceId(R.styleable.PermissionLayout_src, android.R.drawable.ic_btn_speak_now));
        if (a.hasValue(R.styleable.PermissionLayout_iconColor)) {
            mIcon.setColorName(Objects.requireNonNull(a.getString(R.styleable.PermissionLayout_iconColor)));
        }

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
            isExpanded = a.getBoolean(R.styleable.PermissionLayout_expandDescription, false);
            if (isExpanded ^ mExpandableLayout.isExpanded()) {
                mExpandableLayout.setExpanded(isExpanded, false);
                mExpandableButton.setChecked(isExpanded);
            }
        }

        TextView mDescription = findViewById(R.id.description);
        if (a.hasValue(R.styleable.PermissionLayout_description)) {
            mDescription.setText(a.getText(R.styleable.PermissionLayout_description));
        }
        mDescription.setMovementMethod(LinkMovementMethod.getInstance());

        a.recycle();
    }

    public MaterialCircleIconView getTitleIcon() {
        return mIcon;
    }

    @Override
    public void onClick(@Nullable View v) {
        isExpanded = !isExpanded;
        mExpandableButton.setChecked(isExpanded);
        mExpandableLayout.setExpanded(isExpanded);
    }
}
