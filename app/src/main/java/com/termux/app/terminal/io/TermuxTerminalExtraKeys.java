package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private static final String KEY_SCROLL = "SCROLL";

    private ExtraKeysInfo mExtraKeysInfoPageLeft;
    private ExtraKeysInfo mExtraKeysInfo;
    private final List<MaterialButton> mScrollButtons = new ArrayList<>();

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        reloadExtraKeys();
    }


    /**
     * Set the terminal extra keys and style.
     */
    public void reloadExtraKeys() {
        // The mMap stores the extra key and style string values while loading properties.
        // Check {@link TermuxSharedProperties#getExtraKeysInternalPropertyValueFromValue(String)} and
        // {@link TermuxSharedProperties#getExtraKeysStyleInternalPropertyValueFromValue(String)}.
        String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);
        ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
        if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
            Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
            extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
        }

        mExtraKeysInfoPageLeft = createExtraKeysInfo(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_PAGE_LEFT,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_PAGE_LEFT,
            extraKeysStyle);
        mExtraKeysInfo = createExtraKeysInfo(
            TermuxPropertyConstants.KEY_EXTRA_KEYS,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
            extraKeysStyle);
    }

    private ExtraKeysInfo createExtraKeysInfo(String key, String defaultValue, String extraKeysStyle) {
        String extraKeys = (String) mActivity.getProperties().getInternalPropertyValue(key, true);
        try {
            return new ExtraKeysInfo(extraKeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mActivity, "Could not load and set the \"" + key + "\" property from the properties file: " + e.toString(), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + key + "\" property from the properties file: ", e);

            try {
                return new ExtraKeysInfo(defaultValue, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, "Can't create default extra keys", true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e2);
                return null;
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfoPageLeft() {
        return mExtraKeysInfoPageLeft;
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    @Override
    public void onExtraKeyButtonCreated(ExtraKeysView extraKeysView, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (!KEY_SCROLL.equals(buttonInfo.getKey()))
            return;

        button.setText("");
        button.setContentDescription(buttonInfo.getDisplay());
        button.setIconPadding(0);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        mScrollButtons.add(button);
        updateScrollButtonIcon(extraKeysView, button);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if (KEY_SCROLL.equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null) {
                terminalView.mEmulator.toggleAutoScrollDisabled();
                updateScrollButtonIcons();
            }
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

    public void updateScrollButtonIcons() {
        Iterator<MaterialButton> iterator = mScrollButtons.iterator();
        while (iterator.hasNext()) {
            MaterialButton button = iterator.next();
            if (!(button.getParent() instanceof ExtraKeysView)) {
                iterator.remove();
                continue;
            }

            updateScrollButtonIcon((ExtraKeysView) button.getParent(), button);
        }
    }

    private void updateScrollButtonIcon(ExtraKeysView extraKeysView, MaterialButton button) {
        boolean autoScrollDisabled = isAutoScrollDisabled();
        button.setIconResource(autoScrollDisabled ? R.drawable.ic_scroll_lock_filled : R.drawable.ic_scroll_lock);
        button.setIconTint(ColorStateList.valueOf(autoScrollDisabled ?
            extraKeysView.getButtonActiveTextColor() : extraKeysView.getButtonTextColor()));
    }

    private boolean isAutoScrollDisabled() {
        TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
        return terminalView != null && terminalView.mEmulator != null && terminalView.mEmulator.isAutoScrollDisabled();
    }

}
