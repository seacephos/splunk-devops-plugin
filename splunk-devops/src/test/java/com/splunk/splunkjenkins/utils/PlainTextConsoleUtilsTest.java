package com.splunk.splunkjenkins.utils;

import hudson.console.ConsoleNote;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class PlainTextConsoleUtilsTest {
    private byte[] logText = ("\u001B[8mha:////4KIKPqK5tXCDtTm83KR8dOlkGTotzP4liGbxukwLqvjJAA\u001B[0m[Pipeline] }\n" +
            "\u001B[8mha:////4KY/nBiyccGoc9OKNQirqOjwEcX/CTScoTrGPCj/nnzYAAAApB+LCAAAAAAAA\u001B[0m[Pipeline] echo\n" +
            "hello branch-2").getBytes();
    private String expected = "[Pipeline] }\n[Pipeline] echo\nhello branch-2";

    @Test
    public void arrayIndexOf() {
        int MAX_FINDS = 2;
        int idx = 0;
        for (int i = 1; i <= MAX_FINDS + 1; i++) {
            idx = PlainTextConsoleUtils.arrayIndexOf(logText, idx + 1, logText.length, ConsoleNote.POSTAMBLE);
            if (i > MAX_FINDS) {
                Assert.assertTrue("not found in step" + i, idx == -1);
            } else {
                Assert.assertTrue("find the index in step" + i, idx > 0);
            }
        }
    }

    @Test
    public void decodeConsole() {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PlainTextConsoleUtils.decodeConsole(logText, logText.length, bout);
        String console = bout.toString();
        Assert.assertEquals(expected, console);
    }
}