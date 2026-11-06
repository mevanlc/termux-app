package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.logger.Logger;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysLayout;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.termux.extrakeys.SpecialButtonState;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalView;

import org.json.JSONException;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private static final String KEY_SCROLL = "SCROLL";

    private ExtraKeysLayout mExtraKeysLayout;
    private final List<MaterialButton> mScrollButtons = new ArrayList<>();
    private final TerminalView mTerminalView;
    private MaterialButton mLastScrollTapButton;
    private TerminalEmulator mLastScrollTapEmulator;
    private long mLastScrollTapTime;
    private boolean mLastScrollTapWasGameMode;

    /** The {@link SpecialButton} states shared by every {@link ExtraKeysView} panel of the terminal
     * toolbar, so that a modifier tapped on one panel applies to the keys of all of them and stays
     * readable while a panel that does not define it is shown. */
    private final Map<SpecialButton, SpecialButtonState> mSpecialButtons = ExtraKeysView.getDefaultSpecialButtons();

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);
        mTerminalView = terminalView;

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

        mExtraKeysLayout = createExtraKeysLayout(extraKeysStyle);
    }

    private ExtraKeysLayout createExtraKeysLayout(String extraKeysStyle) {
        // The json file takes priority over the property, and the property is used if the file is
        // unset or could not be read.
        String jsonFilePath = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_JSON_FILE, true);
        String extraKeys = jsonFilePath == null ? null : readExtraKeysJsonFile(jsonFilePath);
        String source;
        if (extraKeys != null) {
            source = "the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_JSON_FILE + "\" file at \"" + jsonFilePath + "\"";
        } else {
            source = "the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property of the properties file";
            extraKeys = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
        }

        try {
            return new ExtraKeysLayout(extraKeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mActivity, "Could not load and set the extra keys from " + source + ": " + e, true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the extra keys from " + source + ": ", e);

            try {
                return new ExtraKeysLayout(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
                    TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, "Can't create default extra keys", true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e2);
                return null;
            }
        }
    }

    /**
     * Returns the contents of the {@link TermuxPropertyConstants#KEY_EXTRA_KEYS_JSON_FILE} file at
     * {@code path}, or {@code null} if it could not be read.
     */
    private String readExtraKeysJsonFile(@NonNull String path) {
        StringBuilder extraKeysBuilder = new StringBuilder();
        Error error = FileUtils.readTextFromFile("extra keys json file", path,
            Charset.defaultCharset(), extraKeysBuilder, false);
        if (error != null) {
            Logger.showToast(mActivity, "Could not read the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_JSON_FILE + "\" file at \"" + path + "\": " + error.getMessage(), true);
            Logger.logErrorExtended(LOG_TAG, "Could not read the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_JSON_FILE + "\" file at \"" + path + "\": " + error.getErrorLogString());
            return null;
        }

        return extraKeysBuilder.toString();
    }

    public int getPanelCount() {
        return mExtraKeysLayout == null ? 0 : mExtraKeysLayout.getPanelCount();
    }

    public ExtraKeysLayout.Panel getPanel(int index) {
        return mExtraKeysLayout == null ? null : mExtraKeysLayout.getPanel(index);
    }

    public int getDefaultPanelIndex() {
        return mExtraKeysLayout == null ? 0 : mExtraKeysLayout.getDefaultPanelIndex();
    }

    public int getTextInputPanelIndex() {
        return mExtraKeysLayout == null ? -1 : mExtraKeysLayout.getTextInputPanelIndex();
    }

    public int getMaximumRowCount() {
        return mExtraKeysLayout == null ? 0 : mExtraKeysLayout.getMaximumRowCount();
    }

    /** The {@link SpecialButtonState} map that every {@link ExtraKeysView} panel must be given so
     * that they share their modifier state. */
    public Map<SpecialButton, SpecialButtonState> getSpecialButtons() {
        return mSpecialButtons;
    }

    /**
     * Read the state of a {@link SpecialButton} from {@link #mSpecialButtons}. Unlike
     * {@link ExtraKeysView#readSpecialButton(SpecialButton, boolean)}, this is not tied to a single
     * panel, so a modifier stays applied while a panel that does not define it is shown.
     *
     * @param autoSetInActive Set to {@code true} if the state should be set to inactive if the
     *                        button is active but is not locked.
     * @return Returns {@code null} if the button is not registered, otherwise whether it is active.
     */
    @Nullable
    public Boolean readSpecialButton(SpecialButton specialButton, boolean autoSetInActive) {
        SpecialButtonState state = mSpecialButtons.get(specialButton);
        if (state == null) return null;

        return state.read(autoSetInActive);
    }

    @Override
    public void onExtraKeyButtonCreated(ExtraKeysView extraKeysView, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (buttonInfo.isMacro() || !KEY_SCROLL.equals(buttonInfo.getKey()))
            return;

        button.setText("");
        button.setContentDescription(buttonInfo.getDisplay());
        button.setIconPadding(0);
        button.setIconSize(button.getResources().getDimensionPixelSize(R.dimen.extra_keys_scroll_icon_size));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setOnLongClickListener(view -> {
            mLastScrollTapButton = null;
            if (mTerminalView.mEmulator != null) {
                mTerminalView.setGameModeEnabled(!mTerminalView.mEmulator.isGameModeEnabled());
                updateScrollButtonIcons();
            }
            return true;
        });
        mScrollButtons.add(button);
        updateScrollButtonIcon(extraKeysView, button);
    }

    @Override
    public void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        TerminalEmulator emulator = mTerminalView.mEmulator;
        if (!buttonInfo.isMacro() && KEY_SCROLL.equals(buttonInfo.getKey()) && emulator != null) {
            long now = SystemClock.uptimeMillis();
            if (button == mLastScrollTapButton && emulator == mLastScrollTapEmulator &&
                now - mLastScrollTapTime <= ViewConfiguration.getDoubleTapTimeout()) {
                // Undo the immediate first tap, then toggle game mode from the original state.
                mTerminalView.toggleScrollLock();
                mTerminalView.setGameModeEnabled(!mLastScrollTapWasGameMode);
                mLastScrollTapButton = null;
                updateScrollButtonIcons();
                return;
            }
            mLastScrollTapButton = button;
            mLastScrollTapEmulator = emulator;
            mLastScrollTapTime = now;
            mLastScrollTapWasGameMode = emulator.isGameModeEnabled();
        } else {
            mLastScrollTapButton = null;
        }
        super.onExtraKeyButtonClick(view, buttonInfo, button);
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
            else if (drawerLayout.isDrawerOpen(Gravity.RIGHT))
                drawerLayout.closeDrawer(Gravity.RIGHT);
            else
                drawerLayout.openDrawer(mTermuxTerminalViewClient.getActivity().getDrawerGravity());
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if (KEY_SCROLL.equals(key)) {
            if (mTerminalView.mEmulator != null) {
                mTerminalView.toggleScrollLock();
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
        boolean gameMode = mTerminalView.mEmulator != null && mTerminalView.mEmulator.isGameModeEnabled();
        button.setIconResource(autoScrollDisabled ? R.drawable.ic_scroll_lock_filled : R.drawable.ic_scroll_lock);
        button.setIconTint(ColorStateList.valueOf(autoScrollDisabled ?
            extraKeysView.getButtonActiveTextColor() : extraKeysView.getButtonTextColor()));
        button.setStrokeColor(ColorStateList.valueOf(extraKeysView.getButtonActiveTextColor()));
        float density = button.getResources().getDisplayMetrics().density;
        button.setStrokeWidth(gameMode ? Math.max(1, (int) (density + 0.5f)) : 0);
        button.setContentDescription(button.getContext().getString(gameMode ? R.string.extra_keys_game_mode :
            autoScrollDisabled ? R.string.extra_keys_scroll_lock_on : R.string.extra_keys_scroll_lock_off));
    }

    private boolean isAutoScrollDisabled() {
        return mTerminalView.mEmulator != null && mTerminalView.mEmulator.isAutoScrollDisabled();
    }

}
