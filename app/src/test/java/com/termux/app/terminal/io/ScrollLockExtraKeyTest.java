package com.termux.app.terminal.io;

import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.google.android.material.button.MaterialButton;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScrollLockExtraKeyTest {
    private TerminalView terminal;
    private TermuxTerminalExtraKeys client;
    private ExtraKeysView keys;
    private MaterialButton scroll;

    @Before
    public void setUp() throws Exception {
        Application context = RuntimeEnvironment.getApplication();
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents);
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        ReflectionHelpers.setField(activity, "mProperties", TermuxAppSharedProperties.init(context));
        terminal = new TerminalView(context, null);
        terminal.setTerminalViewClient(new TermuxTerminalViewClientBase());
        TermuxTerminalSessionClientBase sessionClient = new TermuxTerminalSessionClientBase();
        TerminalSession session = new TerminalSession("/unused", "/", new String[0], new String[0], null, sessionClient);
        terminal.mEmulator = new TerminalEmulator(session, 40, 20, 10, 20, null, sessionClient);
        client = new TermuxTerminalExtraKeys(activity, terminal, null, null);
        keys = createPanel();
        scroll = (MaterialButton) keys.getChildAt(0);
    }

    private ExtraKeysView createPanel() throws Exception {
        ExtraKeysView panel = new ExtraKeysView(RuntimeEnvironment.getApplication(), null);
        panel.setExtraKeysViewClient(client);
        panel.reload(new ExtraKeysInfo("[['SCROLL']]", "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES), 100);
        panel.layout(0, 0, 200, 100);
        return panel;
    }

    private void touch(int action, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, 50, y, 0);
        scroll.dispatchTouchEvent(event);
        event.recycle();
    }

    private void tap() {
        touch(MotionEvent.ACTION_DOWN, 50);
        touch(MotionEvent.ACTION_UP, 50);
    }

    private void pauseBetweenGestures() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getDoubleTapTimeout() + 1));
    }

    private void assertState(boolean locked, boolean game) {
        assertEquals(locked, terminal.mEmulator.isAutoScrollDisabled());
        assertEquals(game, terminal.mEmulator.isGameModeEnabled());
        assertEquals(game, scroll.getStrokeWidth() > 0);
        assertEquals(game ? "Game mode" : locked ? "Scroll lock on" : "Scroll lock off", scroll.getContentDescription());
    }

    @Test
    public void singleTapsToggleImmediatelyAndDoubleTapsToggleGameFromEveryState() {
        assertState(false, false);
        tap();
        assertState(true, false);
        pauseBetweenGestures();
        tap();
        assertState(false, false);
        pauseBetweenGestures();

        tap(); tap();
        assertState(true, true);
        pauseBetweenGestures();
        tap(); tap();
        assertState(true, false);
        pauseBetweenGestures();
        tap(); tap();
        assertState(true, true);
        pauseBetweenGestures();
        tap();
        assertState(false, false);
    }

    @Test
    public void longPressTogglesGameWithoutAnotherToggleOnRelease() {
        for (int i = 0; i < 3; i++) {
            touch(MotionEvent.ACTION_DOWN, 50);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(keys.getLongPressTimeout() + 1));
            assertState(true, i != 1);
            touch(MotionEvent.ACTION_UP, 50);
            assertState(true, i != 1);
            pauseBetweenGestures();
        }
    }

    @Test
    public void cancelledAndSwipedAwayPressesDoNotToggle() {
        touch(MotionEvent.ACTION_DOWN, 50);
        touch(MotionEvent.ACTION_CANCEL, 50);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertState(false, false);
        touch(MotionEvent.ACTION_DOWN, 50);
        touch(MotionEvent.ACTION_MOVE, -10);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        touch(MotionEvent.ACTION_UP, -10);
        assertState(false, false);
    }

    @Test
    public void discardingPanelCancelsPendingLongPress() {
        touch(MotionEvent.ACTION_DOWN, 50);
        keys.releaseSpecialButtons();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertState(false, false);
    }

    @Test
    public void everyPanelShowsTheSameGameStateIncludingRebuiltPanels() throws Exception {
        ExtraKeysView other = createPanel();
        MaterialButton otherScroll = (MaterialButton) other.getChildAt(0);
        tap(); tap();
        assertTrue(otherScroll.getStrokeWidth() > 0);
        assertEquals("Game mode", otherScroll.getContentDescription());
        ExtraKeysView rebuilt = createPanel();
        assertTrue(((MaterialButton) rebuilt.getChildAt(0)).getStrokeWidth() > 0);
        pauseBetweenGestures();
        tap();
        assertEquals(0, otherScroll.getStrokeWidth());
        assertEquals("Scroll lock off", otherScroll.getContentDescription());
    }
}
