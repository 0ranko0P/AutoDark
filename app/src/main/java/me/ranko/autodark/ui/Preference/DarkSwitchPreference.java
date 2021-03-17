package me.ranko.autodark.ui.Preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

public class DarkSwitchPreference extends SwitchPreference {

    private View switchView;
    private boolean isSwitchable;

    public DarkSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DarkSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DarkSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DarkSwitchPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        switchView = holder.findViewById(android.R.id.switch_widget);
        if (!isSwitchable) {
            switchView.setVisibility(View.GONE);
        }
    }

    public boolean isSwitchable() {
        return isSwitchable;
    }

    public void setSwitchable(boolean switchable) {
        isSwitchable = switchable;
        if (switchView != null) {
            switchView.setVisibility(switchable ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
