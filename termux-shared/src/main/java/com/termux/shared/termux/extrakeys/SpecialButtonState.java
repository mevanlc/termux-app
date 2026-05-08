package com.termux.shared.termux.extrakeys;

import android.content.res.ColorStateList;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** The {@link Class} that maintains a state of a {@link SpecialButton} */
public class SpecialButtonState {

    /** If special button has been created for the {@link ExtraKeysView}. */
    boolean isCreated = false;
    /** If special button is active. */
    boolean isActive = false;
    /** If special button is locked due to long hold on it and should not be deactivated if its
     * state is read. */
    boolean isLocked = false;

    private static final int STICKY_STROKE_WIDTH_DP = 1;

    List<MaterialButton> buttons = new ArrayList<>();

    ExtraKeysView mExtraKeysView;

    /**
     * Initialize a {@link SpecialButtonState} to maintain state of a {@link SpecialButton}.
     *
     * @param extraKeysView The {@link ExtraKeysView} instance in which the {@link SpecialButton}
     *                      is to be registered.
     */
    public SpecialButtonState(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    /** Set {@link #isCreated}. */
    public void setIsCreated(boolean value) {
        isCreated = value;
    }

    /** Set {@link #isActive}. */
    public void setIsActive(boolean value) {
        isActive = value;
        for (MaterialButton button : buttons) {
            updateButtonState(button);
        }
    }

    /** Set {@link #isLocked}. */
    public void setIsLocked(boolean value) {
        isLocked = value;
        for (MaterialButton button : buttons) {
            updateButtonState(button);
        }
    }

    /** Apply the current active and sticky visual state to a button. */
    public void updateButtonState(MaterialButton button) {
        button.setTextColor(isActive ? mExtraKeysView.getButtonActiveTextColor() : mExtraKeysView.getButtonTextColor());
        if (isActive && isLocked) {
            button.setStrokeColor(ColorStateList.valueOf(mExtraKeysView.getButtonActiveTextColor()));
            button.setStrokeWidth(getStickyStrokeWidth());
        } else {
            button.setStrokeWidth(0);
        }
    }

    private int getStickyStrokeWidth() {
        float density = mExtraKeysView.getResources().getDisplayMetrics().density;
        return Math.max(1, (int) (STICKY_STROKE_WIDTH_DP * density + 0.5f));
    }

}
