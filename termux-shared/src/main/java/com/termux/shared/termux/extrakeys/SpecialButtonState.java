package com.termux.shared.termux.extrakeys;

import android.content.res.ColorStateList;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The {@link Class} that maintains a state of a {@link SpecialButton} */
public class SpecialButtonState {

    /** If special button has been created for an {@link ExtraKeysView}. */
    boolean isCreated = false;
    /** If special button is active. */
    boolean isActive = false;
    /** If special button is locked due to long hold on it and should not be deactivated if its
     * state is read. */
    boolean isLocked = false;

    private static final int STICKY_STROKE_WIDTH_DP = 1;

    /** The buttons created for this special button, grouped by the {@link ExtraKeysView} that
     * created them. A single state is shared by every {@link ExtraKeysView} of the terminal
     * toolbar so that a modifier tapped on one panel applies to the keys of all of them, and
     * grouping by view lets a view drop only its own buttons when it is reloaded or discarded. */
    final Map<ExtraKeysView, List<MaterialButton>> buttons = new LinkedHashMap<>();

    /** Set {@link #isCreated}. */
    public void setIsCreated(boolean value) {
        isCreated = value;
    }

    /** Set {@link #isActive}. */
    public void setIsActive(boolean value) {
        isActive = value;
        updateButtonStates();
    }

    /** Set {@link #isLocked}. */
    public void setIsLocked(boolean value) {
        isLocked = value;
        updateButtonStates();
    }

    /**
     * Read the state of the special button.
     *
     * @param autoSetInActive Set to {@code true} if the state should be set to inactive if the
     *                        button is active but is not locked.
     * @return Returns {@code true} if the button is created and is active, otherwise {@code false}.
     */
    public boolean read(boolean autoSetInActive) {
        if (!isCreated || !isActive)
            return false;

        // Disable active state only if not locked
        if (autoSetInActive && !isLocked)
            setIsActive(false);

        return true;
    }

    /** Register a button created for {@code extraKeysView} so its visual state is kept updated. */
    void addButton(@NonNull ExtraKeysView extraKeysView, @NonNull MaterialButton button) {
        List<MaterialButton> viewButtons = buttons.get(extraKeysView);
        if (viewButtons == null) {
            viewButtons = new ArrayList<>();
            buttons.put(extraKeysView, viewButtons);
        }
        viewButtons.add(button);
    }

    /** Forget the buttons registered for {@code extraKeysView}. */
    void removeButtonsOfView(@NonNull ExtraKeysView extraKeysView) {
        buttons.remove(extraKeysView);
    }

    private void updateButtonStates() {
        for (Map.Entry<ExtraKeysView, List<MaterialButton>> entry : buttons.entrySet()) {
            for (MaterialButton button : entry.getValue())
                updateButtonState(entry.getKey(), button);
        }
    }

    /** Apply the current active and sticky visual state to a button of {@code extraKeysView}. */
    public void updateButtonState(@NonNull ExtraKeysView extraKeysView, @NonNull MaterialButton button) {
        button.setTextColor(isActive ? extraKeysView.getButtonActiveTextColor() : extraKeysView.getButtonTextColor());
        if (isActive && isLocked) {
            button.setStrokeColor(ColorStateList.valueOf(extraKeysView.getButtonActiveTextColor()));
            button.setStrokeWidth(getStickyStrokeWidth(extraKeysView));
        } else {
            button.setStrokeWidth(0);
        }
    }

    private int getStickyStrokeWidth(@NonNull ExtraKeysView extraKeysView) {
        float density = extraKeysView.getResources().getDisplayMetrics().density;
        return Math.max(1, (int) (STICKY_STROKE_WIDTH_DP * density + 0.5f));
    }

}
