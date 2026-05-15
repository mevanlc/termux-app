package com.termux.app;

import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.data.TermuxUrlUtils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;

public class TermuxActivityTest {

    private void assertUrlsAre(String text, String... urls) {
        LinkedHashSet<String> expected = new LinkedHashSet<>();
        Collections.addAll(expected, urls);
        Assert.assertEquals(expected, TermuxUrlUtils.extractUrls(text));
    }

    @Test
    public void testExtractUrls() {
        assertUrlsAre("hello http://example.com world", "http://example.com");

        assertUrlsAre("http://example.com\nhttp://another.com", "http://example.com", "http://another.com");

        assertUrlsAre("hello http://example.com world and http://more.example.com with secure https://more.example.com",
            "http://example.com", "http://more.example.com", "https://more.example.com");

        assertUrlsAre("hello https://example.com/#bar https://example.com/foo#bar",
            "https://example.com/#bar", "https://example.com/foo#bar");
    }

    @Test
    public void testExtractUrlFromTextSelection() {
        Assert.assertEquals("http://example.com",
            TermuxTerminalViewClient.extractUrlFromTextSelection("http://example.com"));
        Assert.assertEquals("https://example.com/foo#bar",
            TermuxTerminalViewClient.extractUrlFromTextSelection("  https://example.com/foo#bar\n"));

        Assert.assertNull(TermuxTerminalViewClient.extractUrlFromTextSelection(null));
        Assert.assertNull(TermuxTerminalViewClient.extractUrlFromTextSelection(""));
        Assert.assertNull(TermuxTerminalViewClient.extractUrlFromTextSelection("hello http://example.com"));
        Assert.assertNull(TermuxTerminalViewClient.extractUrlFromTextSelection("http://example.com\nhttp://another.com"));
    }

}
