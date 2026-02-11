package com.termux.shared.termux.terminal.io;

import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.ArrayList;

import static com.termux.shared.termux.extrakeys.ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS;


public class TerminalExtraKeys implements ExtraKeysView.IExtraKeysView {

    private final TerminalView mTerminalView;
    private long mSleepMacroGeneration = 0;
    private SleepMacroState mSleepMacroState;

    private static final String SLEEP_TOKEN_PREFIX = "SLEEP";

    public TerminalExtraKeys(@NonNull TerminalView terminalView) {
        mTerminalView = terminalView;
        mTerminalView.setOnUserInputListener(this::cancelSleepMacro);
    }

    @Override
    public void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        // Any extra-keys tap cancels an in-progress sleep macro.
        cancelSleepMacro();

        if (buttonInfo.isMacro()) {
            ParsedMacro parsedMacro = parseMacro(buttonInfo.getKey());
            if (parsedMacro.usesSleep) {
                startSleepMacro(view, parsedMacro.steps);
            } else {
                boolean ctrlDown = false;
                boolean altDown = false;
                boolean shiftDown = false;
                boolean fnDown = false;
                for (MacroStep step : parsedMacro.steps) {
                    if (step.type == MacroStepType.MODIFIER) {
                        if (step.modifier == SpecialButton.CTRL) {
                            ctrlDown = true;
                        } else if (step.modifier == SpecialButton.ALT) {
                            altDown = true;
                        } else if (step.modifier == SpecialButton.SHIFT) {
                            shiftDown = true;
                        } else if (step.modifier == SpecialButton.FN) {
                            fnDown = true;
                        }
                    } else {
                        onTerminalExtraKeyButtonClick(view, step.key, ctrlDown, altDown, shiftDown, fnDown);
                        ctrlDown = false;
                        altDown = false;
                        shiftDown = false;
                        fnDown = false;
                    }
                }
            }
        } else {
            onTerminalExtraKeyButtonClick(view, buttonInfo.getKey(), false, false, false, false);
        }
    }

    private void cancelSleepMacro() {
        mSleepMacroGeneration++;
        mSleepMacroState = null;
    }

    private void startSleepMacro(@NonNull View view, @NonNull MacroStep[] steps) {
        long generation = mSleepMacroGeneration;
        mSleepMacroState = new SleepMacroState(generation, view, steps, () -> runSleepMacro(generation));
        mTerminalView.post(mSleepMacroState.runnable);
    }

    private void runSleepMacro(long generation) {
        SleepMacroState state = mSleepMacroState;
        if (state == null || state.generation != generation) return;

        while (state.index < state.steps.length) {
            MacroStep step = state.steps[state.index++];

            if (step.type == MacroStepType.MODIFIER) {
                if (step.modifier == SpecialButton.CTRL) {
                    state.ctrlDown = true;
                } else if (step.modifier == SpecialButton.ALT) {
                    state.altDown = true;
                } else if (step.modifier == SpecialButton.SHIFT) {
                    state.shiftDown = true;
                } else if (step.modifier == SpecialButton.FN) {
                    state.fnDown = true;
                }
                continue;
            }

            if (step.type == MacroStepType.SLEEP) {
                int delay = step.sleepMillis;
                if (delay <= 0) {
                    mTerminalView.post(state.runnable);
                } else {
                    mTerminalView.postDelayed(state.runnable, delay);
                }
                return;
            }

            onTerminalExtraKeyButtonClick(state.view, step.key, state.ctrlDown, state.altDown, state.shiftDown, state.fnDown);
            state.ctrlDown = false;
            state.altDown = false;
            state.shiftDown = false;
            state.fnDown = false;
        }

        if (mSleepMacroState != null && mSleepMacroState.generation == generation) {
            mSleepMacroState = null;
        }
    }

    protected void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if (PRIMARY_KEY_CODES_FOR_STRINGS.containsKey(key)) {
            Integer keyCode = PRIMARY_KEY_CODES_FOR_STRINGS.get(key);
            if (keyCode == null) return;
            int metaState = 0;
            if (ctrlDown) metaState |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            if (altDown) metaState |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
            if (shiftDown) metaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
            if (fnDown) metaState |= KeyEvent.META_FUNCTION_ON;

            KeyEvent keyEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState,
                TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
            mTerminalView.onKeyDown(keyCode, keyEvent);
        } else {
            // not a control char
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                key.codePoints().forEach(codePoint -> {
                    mTerminalView.inputCodePoint(TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD, codePoint, ctrlDown, altDown);
                });
            } else {
                TerminalSession session = mTerminalView.getCurrentSession();
                if (session != null && key.length() > 0)
                    session.write(key);
            }
        }
    }

    @Override
    public boolean performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        return false;
    }

    enum MacroStepType {
        MODIFIER,
        KEY,
        SLEEP
    }

    static final class MacroStep {
        final MacroStepType type;
        final String key;
        final SpecialButton modifier;
        final int sleepMillis;

        private MacroStep(@NonNull MacroStepType type, String key, SpecialButton modifier, int sleepMillis) {
            this.type = type;
            this.key = key;
            this.modifier = modifier;
            this.sleepMillis = sleepMillis;
        }

        static MacroStep modifier(@NonNull SpecialButton modifier) {
            return new MacroStep(MacroStepType.MODIFIER, null, modifier, 0);
        }

        static MacroStep key(@NonNull String key) {
            return new MacroStep(MacroStepType.KEY, key, null, 0);
        }

        static MacroStep sleep(int millis) {
            return new MacroStep(MacroStepType.SLEEP, null, null, millis);
        }
    }

    static final class ParsedMacro {
        final MacroStep[] steps;
        final boolean usesSleep;

        private ParsedMacro(@NonNull MacroStep[] steps, boolean usesSleep) {
            this.steps = steps;
            this.usesSleep = usesSleep;
        }
    }

    private static final class SleepMacroState {
        final long generation;
        final View view;
        final MacroStep[] steps;
        final Runnable runnable;
        int index;
        boolean ctrlDown;
        boolean altDown;
        boolean shiftDown;
        boolean fnDown;

        private SleepMacroState(long generation, @NonNull View view, @NonNull MacroStep[] steps, @NonNull Runnable runnable) {
            this.generation = generation;
            this.view = view;
            this.steps = steps;
            this.runnable = runnable;
        }
    }

    static ParsedMacro parseMacro(@NonNull String macro) {
        String[] tokens = macro.split(" ");
        ArrayList<MacroStep> steps = new ArrayList<>(tokens.length);
        boolean usesSleep = false;

        for (String token : tokens) {
            if (SpecialButton.CTRL.getKey().equals(token)) {
                steps.add(MacroStep.modifier(SpecialButton.CTRL));
            } else if (SpecialButton.ALT.getKey().equals(token)) {
                steps.add(MacroStep.modifier(SpecialButton.ALT));
            } else if (SpecialButton.SHIFT.getKey().equals(token)) {
                steps.add(MacroStep.modifier(SpecialButton.SHIFT));
            } else if (SpecialButton.FN.getKey().equals(token)) {
                steps.add(MacroStep.modifier(SpecialButton.FN));
            } else {
                Integer sleepMillis = parseSleepMillis(token);
                if (sleepMillis != null) {
                    steps.add(MacroStep.sleep(sleepMillis));
                    usesSleep = true;
                } else {
                    steps.add(MacroStep.key(token));
                }
            }
        }

        return new ParsedMacro(steps.toArray(new MacroStep[0]), usesSleep);
    }

    static Integer parseSleepMillis(@NonNull String token) {
        if (!token.startsWith(SLEEP_TOKEN_PREFIX)) return null;

        String digits = token.substring(SLEEP_TOKEN_PREFIX.length());
        if (digits.isEmpty() || digits.length() > 4) return null;

        int millis = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') return null;
            millis = (millis * 10) + (c - '0');
        }

        return millis;
    }

}
