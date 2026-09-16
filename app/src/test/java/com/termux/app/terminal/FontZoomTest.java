package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession.TermuxSessionClient;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class FontZoomTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context context;
    private SharedPreferences storedPreferences;
    private TermuxAppSharedPreferences preferences;
    private TermuxAppSharedProperties properties;
    private TermuxTerminalSessionActivityClient sessionClient;
    private TermuxTerminalViewClient viewClient;
    private TerminalView view;
    private File propertiesFile;
    private TermuxShellManager shellManager;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getResources().getDisplayMetrics().density = 3f;
        storedPreferences = context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE);
        propertiesFile = temporaryFolder.newFile("termux.properties");
        properties = TermuxAppSharedProperties.init(context);
        ReflectionHelpers.setField(properties, "mPropertiesFilePaths",
            Collections.singletonList(propertiesFile.getAbsolutePath()));
        reloadStep("0.25");
        preferences = TermuxAppSharedPreferences.build(context);
        assertNotNull(preferences);

        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        view = new TerminalView(context, null);
        ReflectionHelpers.setField(activity, "mTerminalView", view);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);
        ReflectionHelpers.setField(activity, "mProperties", properties);
        TermuxService service = Robolectric.buildService(TermuxService.class).get();
        shellManager = new TermuxShellManager(context);
        ReflectionHelpers.setField(service, "mShellManager", shellManager);
        ReflectionHelpers.setField(activity, "mTermuxService", service);
        sessionClient = new TermuxTerminalSessionActivityClient(activity);
        viewClient = new TermuxTerminalViewClient(activity, sessionClient);
        view.setTerminalViewClient(viewClient);
    }

    private void reloadStep(String value) throws Exception {
        try (FileWriter writer = new FileWriter(propertiesFile)) {
            writer.write("pinch-zoom-font-size-step=" + value + "\n");
        }
        properties.loadTermuxPropertiesFromDisk();
    }

    @Test
    public void readsOldPixelSizesAndPersistsFractionalZoom() {
        storedPreferences.edit().putString(TERMUX_APP.KEY_FONTSIZE, "30").commit();
        assertEquals(30f, preferences.getFontSize(), 0f);
        assertEquals(30.75f, preferences.changeFontSize(true, 0.75f), 0f);
        assertEquals("30.75", storedPreferences.getString(TERMUX_APP.KEY_FONTSIZE, null));
        assertEquals(30.75f, TermuxAppSharedPreferences.build(context).getFontSize(), 0f);
        assertEquals(30f, preferences.changeFontSize(false, 0.75f), 0f);
    }

    @Test
    public void clampsFractionalZoomAndRejectsNonFiniteSavedSizes() {
        float minimum = preferences.clampFontSize(0f);
        float maximum = preferences.clampFontSize(1000f);
        assertEquals(minimum, preferences.getChangedFontSize(minimum + 0.25f, false, 0.75f), 0f);
        assertEquals(maximum, preferences.getChangedFontSize(maximum - 0.25f, true, 0.75f), 0f);
        float defaultSize = preferences.getFontSize();
        for (String value : new String[] {"NaN", "Infinity", "-Infinity", "invalid"}) {
            storedPreferences.edit().putString(TERMUX_APP.KEY_FONTSIZE, value).commit();
            assertEquals(defaultSize, preferences.getFontSize(), 0f);
        }
    }

    @Test
    public void pinchUsesDensityAndReloadedStepWhileKeyboardKeepsPixelStep() throws Exception {
        for (float density : new float[] {1f, 3f}) {
            view.getResources().getDisplayMetrics().density = density;
            preferences.setFontSize(30f);
            assertEquals(1.05f, viewClient.onScale(1.05f), 0f);
            assertEquals(30f, preferences.getFontSize(), 0f);
            assertEquals(1f, viewClient.onScale(1.2f), 0f);
            assertEquals(30f + 0.25f * density, view.getTextSize(), 0f);
            viewClient.onScale(0.8f);
            assertEquals(30f, view.getTextSize(), 0f);
        }
        reloadStep("0.5");
        viewClient.onScale(1.2f);
        assertEquals(31.5f, view.getTextSize(), 0f);
        viewClient.changeFontSize(true);
        assertEquals(33.5f, view.getTextSize(), 0f);
    }

    @Test
    public void sessionSwitchesKeepFractionalSizes() {
        preferences.setZoomPerSessionEnabled(true);
        TermuxSession first = newSession(30f);
        TermuxSession second = newSession(40.25f);
        view.attachSession(first.getTerminalSession(), first.getFontSize());
        viewClient.onScale(1.2f);
        assertEquals(30.75f, first.getFontSize(), 0f);
        assertEquals(30.75f, preferences.getFontSize(), 0f);
        assertEquals(40.25f, second.getFontSize(), 0f);

        view.attachSession(second.getTerminalSession(), sessionClient.getFontSizeForTermuxSession(second));
        viewClient.onScale(0.8f);
        assertEquals(39.5f, second.getFontSize(), 0f);
        view.attachSession(first.getTerminalSession());
        sessionClient.applyCurrentSessionFontSize();
        assertEquals(30.75f, view.getTextSize(), 0f);
    }

    private TermuxSession newSession(float size) {
        TerminalSession terminal = new TerminalSession("/unused", "/", new String[0], new String[0],
            null, new TermuxTerminalSessionClientBase());
        TermuxSession session = ReflectionHelpers.callConstructor(TermuxSession.class,
            ClassParameter.from(TerminalSession.class, terminal),
            ClassParameter.from(ExecutionCommand.class, new ExecutionCommand()),
            ClassParameter.from(TermuxSessionClient.class, null),
            ClassParameter.from(boolean.class, false));
        session.setFontSize(size);
        shellManager.mTermuxSessions.add(session);
        return session;
    }
}
