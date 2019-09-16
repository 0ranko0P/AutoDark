package me.ranko.autodark.ui.Preference;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.Preference;

import java.time.LocalTime;

import me.ranko.autodark.Utils.DarkTimeUtil;

/**
 * Display preference of Dark mode
 *
 * @author 0rano0P
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class DarkDisplayPreference extends Preference {

    @Nullable
    private Dialog dialog;

    private LocalTime mTime;

    public DarkDisplayPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DarkDisplayPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs);
    }

    public DarkDisplayPreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context, android.R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    public DarkDisplayPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        if (dialog != null) {
            if(dialog.isShowing()) dialog.dismiss();
            dialog = null;
        }
    }

    @Override
    protected void onClick() {
        if (dialog == null) dialog = createDialog(getContext());

        dialog.show();
    }

    private Dialog createDialog(Context context) {
        boolean use24HourFormat = android.text.format.DateFormat.is24HourFormat(context);
        LocalTime pickedTime = getTime();

        return new TimePickerDialog(context, (timePicker, hour, minute) -> {
            LocalTime newTime = LocalTime.of(hour, minute);
            if (!newTime.equals(pickedTime)) {
                setTime(newTime);
                callChangeListener(newTime);
            } },
                pickedTime.getHour(),
                pickedTime.getMinute(),
                use24HourFormat);
    }

    private void setTime(LocalTime time) {
        persistString(DarkTimeUtil.getPersistFormattedString(time));
        mTime = time;
        updateSummary();
        notifyChanged();
    }

    public String getTimeText() {
        return DarkTimeUtil.getPersistFormattedString(mTime);
    }

    public LocalTime getTime() {
        return mTime;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        LocalTime time = DarkTimeUtil.getPersistLocalTime(getPersistedString((String) defaultValue));
        setTime(time);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) return superState;

        final SavedState myState = new SavedState(superState);
        myState.mTime = DarkTimeUtil.getPersistFormattedString(mTime);
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setTime(DarkTimeUtil.getPersistLocalTime(myState.mTime));
    }


    private void updateSummary() {
        setSummary(DarkTimeUtil.getDisplayFormattedString(mTime));
    }

    private static class SavedState extends BaseSavedState {
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };

        String mTime;

        SavedState(Parcel source) {
            super(source);
            mTime = source.readString();
        }

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mTime);
        }
    }
}
