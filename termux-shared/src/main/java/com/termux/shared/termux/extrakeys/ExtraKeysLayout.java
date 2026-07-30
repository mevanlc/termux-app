package com.termux.shared.termux.extrakeys;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The ordered list of extra keys panels shown by the terminal toolbar. Each panel is a page of the
 * toolbar that the user swipes between.
 *
 * Two input formats are accepted and are told apart by the type of the top level json value.
 *
 * <b>Classic format (top level array)</b> — the format documented by {@link ExtraKeysInfo}, a json
 * array of rows of keys. It defines a single keys panel, and a {@link #PRESET_TEXT_INPUT_VIEW}
 * panel is appended after it:
 * {@code
 * [['ESC','/','HOME','UP','END'], ['TAB','CTRL','ALT','LEFT','DOWN']]
 * }
 *
 * <b>Panels format (top level object)</b> — each entry of the object defines one panel, and the
 * panels are shown in the order they are written. The entry name is a descriptive name chosen by
 * the user and has no meaning to the app other than appearing in error messages. The only reserved
 * name is {@link #KEY_SCHEMA}, whose value is informational and is not parsed.
 * {@code
 * {
 *   "$schema": "https://termux.com/schemas/extra-keys-v2.schema.json",
 *   "function": {
 *     "keys": [["F1","F2","F3","F4","F5","F6"], ["F7","F8","F9","F10","F11","F12"]]
 *   },
 *   "main": {
 *     "default": true,
 *     "keys": [["ESC","TAB","CTRL","ALT","UP","DOWN"], ["LEFT","RIGHT","HOME","END","PGUP","PGDN"]]
 *   },
 *   "text_input": {
 *     "preset": "text_input_view"
 *   }
 * }
 * }
 *
 * A panel entry must define exactly one of:
 * - {@link #KEY_KEYS}: a json array of rows of keys, in the format documented by {@link ExtraKeysInfo}.
 * - {@link #KEY_PRESET}: the name of a built in panel. {@link #PRESET_TEXT_INPUT_VIEW} is currently
 *   the only one, and it pulls in the text input view that would otherwise be appended as the last
 *   panel. At most one panel may use it.
 *
 * A panel entry may also set {@link #KEY_DEFAULT} to {@code true} to mark it as the panel shown
 * when the toolbar is first built. If no panel is marked, the middle panel is used, which for an
 * even number of panels is the one before the middle.
 */
public class ExtraKeysLayout {

    /** The reserved name for the informational json schema declaration of a panels layout. */
    public static final String KEY_SCHEMA = "$schema";

    /** The name for the json array of rows of keys of a panel. */
    public static final String KEY_KEYS = "keys";

    /** The name for the boolean that marks a panel as the one shown when the toolbar is built. */
    public static final String KEY_DEFAULT = "default";

    /** The name for the built in panel a panel should show instead of its own keys. */
    public static final String KEY_PRESET = "preset";

    /** The {@link #KEY_PRESET} value for the terminal toolbar text input view. */
    public static final String PRESET_TEXT_INPUT_VIEW = "text_input_view";

    /** The name given to the {@link #PRESET_TEXT_INPUT_VIEW} panel appended to a layout that does
     * not declare one itself. */
    public static final String DEFAULT_TEXT_INPUT_PANEL_NAME = "text_input";

    /** The name given to the single keys panel of a classic format layout. */
    public static final String DEFAULT_KEYS_PANEL_NAME = "extra-keys";

    private static final String LOG_TAG = "ExtraKeysLayout";

    /** A single page of the terminal toolbar. */
    public static class Panel {

        @NonNull private final String mName;
        @Nullable private final ExtraKeysInfo mExtraKeysInfo;
        @Nullable private final String mPreset;
        private final boolean mIsDefault;

        private Panel(@NonNull String name, @Nullable ExtraKeysInfo extraKeysInfo,
                      @Nullable String preset, boolean isDefault) {
            mName = name;
            mExtraKeysInfo = extraKeysInfo;
            mPreset = preset;
            mIsDefault = isDefault;
        }

        static Panel forKeys(@NonNull String name, @NonNull ExtraKeysInfo extraKeysInfo, boolean isDefault) {
            return new Panel(name, extraKeysInfo, null, isDefault);
        }

        static Panel forPreset(@NonNull String name, @NonNull String preset, boolean isDefault) {
            return new Panel(name, null, preset, isDefault);
        }

        /** The descriptive name of the panel. */
        @NonNull
        public String getName() {
            return mName;
        }

        /** The keys of the panel, or {@code null} if the panel shows a {@link #getPreset()} instead. */
        @Nullable
        public ExtraKeysInfo getExtraKeysInfo() {
            return mExtraKeysInfo;
        }

        /** The built in panel to show, or {@code null} if the panel defines its own keys. */
        @Nullable
        public String getPreset() {
            return mPreset;
        }

        /** If the panel is the one to show when the toolbar is built. */
        public boolean isDefault() {
            return mIsDefault;
        }

        /** If the panel shows the terminal toolbar text input view. */
        public boolean isTextInputView() {
            return PRESET_TEXT_INPUT_VIEW.equals(mPreset);
        }

    }

    @NonNull private final List<Panel> mPanels;
    private final int mDefaultPanelIndex;

    /**
     * Initialize an {@link ExtraKeysLayout}.
     *
     * @param propertiesInfo The {@link String} containing the layout. Check the class javadoc for details.
     * @param style The style to pass to {@link ExtraKeysInfo#getCharDisplayMapForStyle(String)}.
     * @param extraKeyAliasMap The {@link ExtraKeysConstants.ExtraKeyDisplayMap} that defines the
     *                         aliases for the actual key names.
     */
    public ExtraKeysLayout(@NonNull String propertiesInfo, String style,
                           @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        this(propertiesInfo, ExtraKeysInfo.getCharDisplayMapForStyle(style), extraKeyAliasMap);
    }

    /**
     * Initialize an {@link ExtraKeysLayout}.
     *
     * @param propertiesInfo The {@link String} containing the layout. Check the class javadoc for details.
     * @param extraKeyDisplayMap The {@link ExtraKeysConstants.ExtraKeyDisplayMap} that defines the
     *                           display text mapping for the keys.
     * @param extraKeyAliasMap The {@link ExtraKeysConstants.ExtraKeyDisplayMap} that defines the
     *                         aliases for the actual key names.
     */
    public ExtraKeysLayout(@NonNull String propertiesInfo,
                           @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap,
                           @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        mPanels = Collections.unmodifiableList(initPanels(propertiesInfo, extraKeyDisplayMap, extraKeyAliasMap));
        mDefaultPanelIndex = findDefaultPanelIndex(mPanels);
    }

    @NonNull
    private static List<Panel> initPanels(@NonNull String propertiesInfo,
                                          @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap,
                                          @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        // Parsing the top level value instead of sniffing the first character of the string keeps
        // the lax syntax of org.json, which allows comments before the value.
        Object root = new JSONTokener(propertiesInfo).nextValue();

        if (root instanceof JSONArray)
            return initClassicPanels((JSONArray) root, extraKeyDisplayMap, extraKeyAliasMap);
        else if (root instanceof JSONObject)
            return initPanelsFromObject((JSONObject) root, extraKeyDisplayMap, extraKeyAliasMap);
        else
            throw new JSONException("The extra keys must either be an array of rows of keys or an object of named panels");
    }

    @NonNull
    private static List<Panel> initClassicPanels(@NonNull JSONArray rows,
                                                 @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap,
                                                 @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        List<Panel> panels = new ArrayList<>(2);
        panels.add(Panel.forKeys(DEFAULT_KEYS_PANEL_NAME,
            new ExtraKeysInfo(rows, extraKeyDisplayMap, extraKeyAliasMap), false));
        panels.add(newTextInputPanel());
        return panels;
    }

    @NonNull
    private static List<Panel> initPanelsFromObject(@NonNull JSONObject root,
                                                    @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap,
                                                    @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        List<Panel> panels = new ArrayList<>();
        boolean textInputDeclared = false;

        // JSONObject preserves the order the names were parsed in, which is the order the panels
        // are shown in.
        Iterator<String> names = root.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (KEY_SCHEMA.equals(name)) continue;

            Object value = root.get(name);
            if (!(value instanceof JSONObject))
                throw new JSONException("The \"" + name + "\" panel must be an object defining \"" + KEY_KEYS + "\" or \"" + KEY_PRESET + "\"");
            JSONObject panelObject = (JSONObject) value;

            boolean isDefault = panelObject.optBoolean(KEY_DEFAULT, false);
            boolean hasKeys = panelObject.has(KEY_KEYS);
            boolean hasPreset = panelObject.has(KEY_PRESET);

            if (hasKeys && hasPreset)
                throw new JSONException("The \"" + name + "\" panel must not define both \"" + KEY_KEYS + "\" and \"" + KEY_PRESET + "\"");

            if (hasPreset) {
                String preset = panelObject.getString(KEY_PRESET);
                if (!PRESET_TEXT_INPUT_VIEW.equals(preset))
                    throw new JSONException("The \"" + name + "\" panel defines the unknown \"" + KEY_PRESET + "\" value \"" + preset + "\". The only supported preset is \"" + PRESET_TEXT_INPUT_VIEW + "\"");
                if (textInputDeclared)
                    throw new JSONException("The \"" + name + "\" panel defines a second \"" + PRESET_TEXT_INPUT_VIEW + "\" panel. Only one panel may use it");
                textInputDeclared = true;
                panels.add(Panel.forPreset(name, preset, isDefault));
            } else if (hasKeys) {
                Object keys = panelObject.get(KEY_KEYS);
                if (!(keys instanceof JSONArray))
                    throw new JSONException("The \"" + KEY_KEYS + "\" of the \"" + name + "\" panel must be an array of rows of keys");
                panels.add(Panel.forKeys(name,
                    new ExtraKeysInfo((JSONArray) keys, extraKeyDisplayMap, extraKeyAliasMap), isDefault));
            } else {
                throw new JSONException("The \"" + name + "\" panel must define either \"" + KEY_KEYS + "\" or \"" + KEY_PRESET + "\"");
            }
        }

        if (panels.isEmpty())
            throw new JSONException("The extra keys must define at least one panel");

        // The text input view is only appended if the layout did not place it itself.
        if (!textInputDeclared)
            panels.add(newTextInputPanel());

        return panels;
    }

    @NonNull
    private static Panel newTextInputPanel() {
        return Panel.forPreset(DEFAULT_TEXT_INPUT_PANEL_NAME, PRESET_TEXT_INPUT_VIEW, false);
    }

    private static int findDefaultPanelIndex(@NonNull List<Panel> panels) {
        int defaultPanelIndex = -1;
        for (int i = 0; i < panels.size(); i++) {
            if (!panels.get(i).isDefault()) continue;
            if (defaultPanelIndex < 0)
                defaultPanelIndex = i;
            else
                Logger.logWarn(LOG_TAG, "The \"" + panels.get(i).getName() + "\" panel also sets \"" + KEY_DEFAULT + "\". Using the \"" + panels.get(defaultPanelIndex).getName() + "\" panel instead.");
        }

        if (defaultPanelIndex >= 0) return defaultPanelIndex;

        // Without an explicit default, use the middle panel, or the one before it if there is an
        // even number of panels.
        return (panels.size() - 1) / 2;
    }

    /** The panels in the order they are shown in. Never empty. */
    @NonNull
    public List<Panel> getPanels() {
        return mPanels;
    }

    public int getPanelCount() {
        return mPanels.size();
    }

    /** The {@link Panel} at {@code index}, or {@code null} if the index is out of bounds. */
    @Nullable
    public Panel getPanel(int index) {
        if (index < 0 || index >= mPanels.size()) return null;
        return mPanels.get(index);
    }

    /** The index of the panel to show when the toolbar is built. */
    public int getDefaultPanelIndex() {
        return mDefaultPanelIndex;
    }

    /** The index of the {@link #PRESET_TEXT_INPUT_VIEW} panel, or {@code -1} if there is none. */
    public int getTextInputPanelIndex() {
        for (int i = 0; i < mPanels.size(); i++) {
            if (mPanels.get(i).isTextInputView()) return i;
        }
        return -1;
    }

    /**
     * The number of rows of the tallest keys panel, which is the number of rows the toolbar must be
     * tall enough for. A layout of only preset panels reports one row so that they stay visible,
     * but a keys panel with no rows still reports zero, which collapses the toolbar as it does
     * for an empty classic layout.
     */
    public int getMaximumRowCount() {
        int rowCount = 0;
        boolean hasKeysPanel = false;
        for (Panel panel : mPanels) {
            ExtraKeysInfo extraKeysInfo = panel.getExtraKeysInfo();
            if (extraKeysInfo == null) continue;
            hasKeysPanel = true;
            rowCount = Math.max(rowCount, extraKeysInfo.getMatrix().length);
        }
        return hasKeysPanel ? rowCount : 1;
    }

}
