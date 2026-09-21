package com.termux.terminal;

import android.app.Application;
import android.os.Looper;
import android.os.Message;

import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, shadows = SynchronizedSessionTest.ShadowJNI.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SynchronizedSessionTest {
    private TerminalSession session;
    private int updates, finishes;

    @Before
    public void setUp() {
        TermuxTerminalSessionClientBase client = new TermuxTerminalSessionClientBase() {
            @Override public void onTextChanged(TerminalSession session) { updates++; }
            @Override public void onSessionFinished(TerminalSession session) { finishes++; }
        };
        session = new TerminalSession("/unused", "/", new String[0], new String[0], 10, client);
        session.mEmulator = new TerminalEmulator(session, 40, 3, 10, 10, 10, client);
        ShadowJNI.closes = 0;
    }

    private void receive(String text, boolean exited) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        session.mProcessToTerminalIOQueue.write(bytes, 0, bytes.length);
        Message message = Message.obtain();
        message.what = exited ? 4 : 1;
        message.obj = exited ? 1 : null;
        session.mMainThreadHandler.handleMessage(message);
        message.recycle();
    }

    @Test
    public void completedFrameIsPublishedEvenIfNextFrameBeginsInSameRead() {
        receive("old\033[?2026h\rnew", false);
        updates = 0;
        receive("\033[?2026l\033[?2026h\rpartial", false);
        assertEquals(1, updates);
        assertTrue(session.mEmulator.isSyncUpdate());
        assertEquals("new", session.mEmulator.getScreenForRendering().getTranscriptText());
    }

    @Test
    public void timeoutReleasesAndPublishesExactlyOnce() {
        receive("old\033[?2026h\rnew", false);
        updates = 0;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1999));
        assertTrue(session.mEmulator.isSyncUpdate());
        assertEquals(0, updates);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertFalse(session.mEmulator.isSyncUpdate());
        assertEquals("new", session.mEmulator.getScreenForRendering().getTranscriptText());
        assertEquals(1, updates);
    }

    @Test
    public void repeatedStartRenewsTimeoutWithoutPublishingPartialFrame() {
        receive("old\033[?2026h\rnew", false);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        receive("\033[?2026h", false);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1001));
        assertTrue(session.mEmulator.isSyncUpdate());
        assertEquals("old", session.mEmulator.getScreenForRendering().getTranscriptText());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(999));
        assertFalse(session.mEmulator.isSyncUpdate());
    }

    @Test
    public void failedProcessReleasesSnapshotAndShowsExitMessage() {
        receive("old\033[?2026h\rcrash details", true);
        assertFalse(session.mEmulator.isSyncUpdate());
        assertEquals(1, finishes);
        assertEquals(1, ShadowJNI.closes);
        String display = session.mEmulator.getScreenForRendering().getTranscriptText();
        assertTrue(display.contains("crash details"));
        assertTrue(display.contains("Process completed (code 1)"));
        int updatesAfterExit = updates;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals(updatesAfterExit, updates);
    }

    @Implements(className = "com.termux.terminal.JNI")
    public static class ShadowJNI {
        static int closes;
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static void close(int fd) { closes++; }
    }
}
