# PLAN: Extra-Keys Macro `SLEEP####` Delays

## Summary
Add support for an instruction token `SLEEP\\d{1,4}` (uppercase `SLEEP` only) inside extra-keys macros to introduce an N-millisecond delay during macro replay, without blocking the UI thread. Macros that use `SLEEP####` are pre-parsed before execution, only one such macro may run at a time, and user input (including hardware keyboards and volume keys) cancels the running sleep-macro.

## Goals
- Support token `SLEEP\\d{1,4}` (e.g. `SLEEP250`, `SLEEP0`) in `{macro: "..."}`.
- Delay is non-blocking (scheduled, not `Thread.sleep`).
- Pre-parse the full macro string before executing to identify `SLEEP####` usage.
- Only one `SLEEP`-utilizing macro may run at a time.
- User input cancels a running `SLEEP`-utilizing macro (hardware keyboards, volume keys, IME text, etc.).
- Keep `SLEEP####` visible in auto-generated button labels (no filtering from `display` generation).
- Preserve existing macro semantics for modifiers (`CTRL/ALT/SHIFT/FN`) and tokenization.

## Non-goals
- Adding new macro syntax beyond `SLEEP\\d{1,4}`.
- Making tokens case-insensitive (only `SLEEP####` is recognized; `sleep250` is treated as literal text).
- Changing how existing non-sleep macros execute (they should remain immediate/re-entrant as today).

## Current State (as-is)
- Extra-keys macros are space-separated tokens (`{macro: "CTRL f d"}`).
- Macro replay is immediate and synchronous in:
  - `termux-app/termux-shared/src/main/java/com/termux/shared/termux/terminal/io/TerminalExtraKeys.java`
- Modifier tokens set one-shot state for the next non-modifier token, then reset.

## Proposed Design

### 1) Up-front parsing
When an extra-keys button is clicked and `buttonInfo.isMacro()` is true:
- Parse `buttonInfo.getKey()` into a list of tokens split on spaces (same as today).
- Convert into a list of typed steps:
  - `ModifierStep(CTRL|ALT|SHIFT|FN)`
  - `SleepStep(ms)` when token matches `^SLEEP(\\d{1,4})$`
  - `KeyStep(token)` for everything else
- Determine `usesSleep` if any `SleepStep` exists.

Notes:
- `SLEEP0` is valid (treated as “yield”: schedule continuation via `post()` rather than immediate recursion).
- Tokens like `SLEEP` (no digits) or `SLEEP10000` (5 digits) are treated as literal `KeyStep`.
- Modifiers should not be cleared by `SleepStep` (pending modifiers carry across sleeps).

### 2) Cancelable replayer for sleep-macros
If `usesSleep == false`: keep current synchronous replay behavior.

If `usesSleep == true`:
- Execute the parsed steps via a single active “sleep macro runner” per `TerminalExtraKeys`/`TerminalView`.
- Runner implementation:
  - Maintains cursor index into steps and current modifier state.
  - Runs on the main thread but never blocks: it processes until it hits a `SleepStep`, then schedules a continuation using `Handler(Looper.getMainLooper()).postDelayed(...)`.
  - Uses a monotonically increasing `sequenceId` (generation) to ensure cancellation is cheap and reliable (continuations check `sequenceId` before doing work).
- Starting a new sleep-macro cancels any currently running sleep-macro first.

### 3) Cancellation (hardware keyboards + volume keys included)
Cancellation is driven by the terminal input pipeline so it covers:
- Hardware keyboard presses
- Dedicated volume key presses (when mapped as virtual modifier keys)
- IME actions that generate characters (and IME-generated key events like DEL)

Implementation approach:
- In `TerminalExtraKeys.onExtraKeyButtonClick(...)`, cancel any running sleep-macro runner before handling the click (extra-keys tap always cancels).
- Add a lightweight input observer in `TerminalView`:
  - Invoke from `TerminalView.onKeyDown(...)` for key events.
  - Invoke from `TerminalView.inputCodePoint(...)` for codepoint input.
  - The observer cancels the active sleep-macro runner when the input is determined to be user-driven (i.e. not macro-generated).

### 4) Prevent self-cancellation (source tagging)
Macro-generated input must be marked so it does not cancel the macro that produced it.

Planned tagging:
- Use the existing notion of “virtual keyboard”:
  - For macro-generated codepoints: keep using `TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD` (currently `KeyCharacterMap.VIRTUAL_KEYBOARD`, typically `-1`).
  - For macro-generated key events: construct `KeyEvent` with `deviceId = TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD` so `event.getDeviceId()` is `-1`.
- Cancellation logic in `TerminalView` treats:
  - `event.getDeviceId() == KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD` as non-user (do not cancel).
  - `eventSource == KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD` as non-user (do not cancel).
  - everything else as user input (cancel).

This avoids needing new public constants and covers hardware keyboards, IME key events, and volume keys.

## Files Expected to Change
- `termux-app/termux-shared/src/main/java/com/termux/shared/termux/terminal/io/TerminalExtraKeys.java`
  - Add parser + runner + cancellation hooks.
- `termux-app/terminal-view/src/main/java/com/termux/view/TerminalView.java`
  - Add optional input observer and (if needed) a macro event-source constant.
- `termux-app/terminal-view/src/main/java/com/termux/view/TerminalViewClient.java`
  - No changes preferred (avoid interface churn).
- `termux-app/termux-shared/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysInfo.java`
  - Update javadoc/examples to document `SLEEP####` usage and behavior.

## Implementation Steps
1. Add macro token parser that identifies `SLEEP\\d{1,4}` and produces typed steps.
2. Add cancelable sleep-macro runner (handler-based continuation, generation id).
3. Add cancellation triggers:
   - Cancel on extra-keys button click (any key).
   - Cancel on “other terminal input” via `TerminalView` input observer.
4. Ensure macro-generated input is tagged to not self-cancel.
5. Update `ExtraKeysInfo` docs with examples and edge cases (`SLEEP0`, invalid tokens treated as literal).
6. Add unit tests for parsing and runner scheduling/cancellation logic where feasible.

## Testing / Validation
- Unit tests (JVM) for parsing:
  - Accepts `SLEEP0`, `SLEEP250`, `SLEEP9999`.
  - Rejects `sleep250`, `SLEEP`, `SLEEP10000` (treated as literal key tokens).
  - Preserves token order and modifier carry-over across sleeps.
- Manual validation in app:
  - Configure a macro like `{macro: "CTRL f SLEEP250 d", display: "tmux exit SLEEP250"}` and verify:
    - `CTRL f` is sent, then ~250ms later `d`.
  - Start a long sleep macro and press a hardware key (including volume keys); verify macro cancels and no later tokens are emitted.
  - Start a sleep macro and tap another extra key; verify the macro cancels immediately.

## Risks & Mitigations
- **API surface in `terminal-view`:** adding an observer is an API change; keep it optional, default no-op, and avoid modifying `TerminalViewClient`.
- **Reliable source tagging for key events:** ensure we can distinguish macro-generated key events from user key events; if not, implement a guarded fallback and document limitations.
- **Interleaving/ordering:** cancellation must invalidate already-posted continuations; use generation IDs rather than attempting to remove every posted runnable.

## Open Questions
- None remaining for semantics (per agreement): `SLEEP` is uppercase only and `SLEEP0` is valid.
