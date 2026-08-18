package com.termux.shared.termux.settings.properties;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TermuxSharedPropertiesTest {

    @Test
    public void testSessionRowHeightConstants() {
        Assert.assertEquals("session-row-height", TermuxPropertyConstants.KEY_SESSION_ROW_HEIGHT);
        Assert.assertEquals(0, TermuxPropertyConstants.IVALUE_SESSION_ROW_HEIGHT_MIN);
        Assert.assertEquals(500, TermuxPropertyConstants.IVALUE_SESSION_ROW_HEIGHT_MAX);
        Assert.assertEquals(0, TermuxPropertyConstants.DEFAULT_IVALUE_SESSION_ROW_HEIGHT);
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST.contains(TermuxPropertyConstants.KEY_SESSION_ROW_HEIGHT));
    }

    @Test
    public void testSessionRowHeightParsing() {
        Assert.assertEquals(0, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue(null));
        Assert.assertEquals(0, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue(""));
        Assert.assertEquals(0, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue("0"));
        Assert.assertEquals(24, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue("24"));
        Assert.assertEquals(48, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue("48"));
        Assert.assertEquals(64, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue("64"));
        Assert.assertEquals(500, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue("500"));

        // Out of range or invalid falls back to default 0
        Assert.assertEquals(0, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue("-1"));
        Assert.assertEquals(0, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue("501"));
        Assert.assertEquals(0, TermuxSharedProperties.getSessionRowHeightInternalPropertyValueFromValue("invalid"));
    }

    @Test
    public void testInternalPropertyValueForSessionRowHeight() {
        Object result = TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            null,
            TermuxPropertyConstants.KEY_SESSION_ROW_HEIGHT,
            "36"
        );
        Assert.assertEquals(36, result);

        Object defaultResult = TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            null,
            TermuxPropertyConstants.KEY_SESSION_ROW_HEIGHT,
            null
        );
        Assert.assertEquals(0, defaultResult);
    }

    @Test
    public void testSessionListBottomUpConstants() {
        Assert.assertEquals("session-list-bottom-up", TermuxPropertyConstants.KEY_SESSION_LIST_BOTTOM_UP);
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST.contains(TermuxPropertyConstants.KEY_SESSION_LIST_BOTTOM_UP));
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST.contains(TermuxPropertyConstants.KEY_SESSION_LIST_BOTTOM_UP));
    }

    @Test
    public void testSessionListBottomUpParsing() {
        Assert.assertFalse((boolean) TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            null, TermuxPropertyConstants.KEY_SESSION_LIST_BOTTOM_UP, null
        ));
        Assert.assertFalse((boolean) TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            null, TermuxPropertyConstants.KEY_SESSION_LIST_BOTTOM_UP, ""
        ));
        Assert.assertFalse((boolean) TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            null, TermuxPropertyConstants.KEY_SESSION_LIST_BOTTOM_UP, "false"
        ));
        Assert.assertTrue((boolean) TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            null, TermuxPropertyConstants.KEY_SESSION_LIST_BOTTOM_UP, "true"
        ));
        Assert.assertTrue((boolean) TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            null, TermuxPropertyConstants.KEY_SESSION_LIST_BOTTOM_UP, "TRUE"
        ));
    }

}
