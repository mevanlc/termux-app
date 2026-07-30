package com.termux.shared.termux.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ExtraKeysLayoutTest {

    private static ExtraKeysLayout layoutOf(String json) throws JSONException {
        return new ExtraKeysLayout(json, "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
    }

    private static void assertRejects(String json, String expectedMessageFragment) {
        try {
            layoutOf(json);
            fail("Expected a JSONException for: " + json);
        } catch (JSONException e) {
            assertTrue("Unexpected message: " + e.getMessage(),
                e.getMessage() != null && e.getMessage().contains(expectedMessageFragment));
        }
    }

    @Test
    public void classicArrayBecomesOneKeysPanelAndATextInputPanel() throws JSONException {
        ExtraKeysLayout layout = layoutOf("[['ESC','TAB'],['CTRL','ALT']]");

        List<ExtraKeysLayout.Panel> panels = layout.getPanels();
        assertEquals(2, panels.size());

        assertFalse(panels.get(0).isTextInputView());
        assertNotNull(panels.get(0).getExtraKeysInfo());
        assertEquals(2, panels.get(0).getExtraKeysInfo().getMatrix().length);

        assertTrue(panels.get(1).isTextInputView());
        assertNull(panels.get(1).getExtraKeysInfo());
        assertEquals(1, layout.getTextInputPanelIndex());
    }

    @Test
    public void panelsAreShownInTheOrderTheyAreWrittenIn() throws JSONException {
        ExtraKeysLayout layout = layoutOf(
            "{'$schema': 'https://termux.com/schemas/extra-keys-v2.schema.json'," +
                "'function': {'keys': [['F1','F2']]}," +
                "'main': {'keys': [['ESC','TAB']]}," +
                "'symbols': {'keys': [['-','_']]}}");

        assertEquals(4, layout.getPanelCount());
        assertEquals("function", layout.getPanel(0).getName());
        assertEquals("main", layout.getPanel(1).getName());
        assertEquals("symbols", layout.getPanel(2).getName());
        assertEquals(ExtraKeysLayout.DEFAULT_TEXT_INPUT_PANEL_NAME, layout.getPanel(3).getName());
    }

    @Test
    public void declaredTextInputPanelIsNotAppendedAgain() throws JSONException {
        ExtraKeysLayout layout = layoutOf(
            "{'text_input': {'preset': 'text_input_view'}," +
                "'main': {'keys': [['ESC','TAB']]}}");

        assertEquals(2, layout.getPanelCount());
        assertEquals(0, layout.getTextInputPanelIndex());
        assertEquals("main", layout.getPanel(1).getName());
    }

    @Test
    public void defaultMarkerSelectsThePanel() throws JSONException {
        ExtraKeysLayout layout = layoutOf(
            "{'function': {'keys': [['F1']]}," +
                "'main': {'keys': [['ESC']]}," +
                "'text_input': {'default': true, 'preset': 'text_input_view'}}");

        assertEquals(2, layout.getDefaultPanelIndex());
    }

    @Test
    public void theFirstOfSeveralDefaultMarkersWins() throws JSONException {
        ExtraKeysLayout layout = layoutOf(
            "{'a': {'keys': [['A']]}," +
                "'b': {'default': true, 'keys': [['B']]}," +
                "'c': {'default': true, 'keys': [['C']]}}");

        assertEquals(1, layout.getDefaultPanelIndex());
    }

    @Test
    public void withoutADefaultMarkerTheMiddlePanelIsUsed() throws JSONException {
        // 2 panels (one keys panel plus the appended text input) -> index 0
        assertEquals(0, layoutOf("[['ESC']]").getDefaultPanelIndex());

        // 3 panels -> index 1
        assertEquals(1, layoutOf("{'a': {'keys': [['A']]}, 'b': {'keys': [['B']]}}").getDefaultPanelIndex());

        // 4 panels -> the one before the middle, index 1
        assertEquals(1, layoutOf(
            "{'a': {'keys': [['A']]}, 'b': {'keys': [['B']]}, 'c': {'keys': [['C']]}}").getDefaultPanelIndex());

        // 5 panels -> index 2
        assertEquals(2, layoutOf(
            "{'a': {'keys': [['A']]}, 'b': {'keys': [['B']]}, 'c': {'keys': [['C']]}, 'd': {'keys': [['D']]}}")
            .getDefaultPanelIndex());
    }

    @Test
    public void maximumRowCountIsThatOfTheTallestKeysPanel() throws JSONException {
        ExtraKeysLayout layout = layoutOf(
            "{'a': {'keys': [['A']]}," +
                "'b': {'keys': [['B'],['B'],['B']]}," +
                "'text_input': {'preset': 'text_input_view'}}");

        assertEquals(3, layout.getMaximumRowCount());
    }

    @Test
    public void aLayoutOfOnlyPresetPanelsStaysVisible() throws JSONException {
        assertEquals(1, layoutOf("{'text_input': {'preset': 'text_input_view'}}").getMaximumRowCount());

        // An empty classic layout still collapses the toolbar, as it does upstream.
        assertEquals(0, layoutOf("[]").getMaximumRowCount());
    }

    @Test
    public void commentsAndUnquotedNamesAreAccepted() throws JSONException {
        ExtraKeysLayout layout = layoutOf(
            "// the panels\n" +
                "{ /* keys */ main: {keys: [[ESC, TAB]]} }");

        assertEquals(2, layout.getPanelCount());
        assertEquals("main", layout.getPanel(0).getName());
    }

    @Test
    public void invalidLayoutsAreRejected() {
        assertRejects("\"not a layout\"", "must either be an array of rows of keys or an object of named panels");
        assertRejects("{'main': 'ESC'}", "must be an object defining");
        assertRejects("{'main': {}}", "must define either");
        assertRejects("{'main': {'keys': [['ESC']], 'preset': 'text_input_view'}}", "must not define both");
        assertRejects("{'main': {'preset': 'nope'}}", "unknown \"preset\" value");
        assertRejects("{'a': {'preset': 'text_input_view'}, 'b': {'preset': 'text_input_view'}}", "Only one panel may use it");
        assertRejects("{'main': {'keys': 'ESC'}}", "must be an array of rows of keys");
        assertRejects("{'$schema': 'x'}", "at least one panel");
    }

}
