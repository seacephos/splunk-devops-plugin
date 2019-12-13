package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.listeners.LoggingConfigListener;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemConfigTest {
    @Test
    public void testConfigXmlIgnored() {
        assertTrue(LoggingConfigListener.IGNORED.matcher("/opt/jenkins/queue.xml").find());
        assertTrue(LoggingConfigListener.IGNORED.matcher("/opt/jenkins/hudson.model.UpdateCenter.xml").find());
        assertTrue(LoggingConfigListener.IGNORED.matcher("/opt/jenkins/job/foo/builds/1/config.xml").find());
        assertFalse(LoggingConfigListener.IGNORED.matcher("/opt/jenkins/job/foo/config.xml").find());
    }
}
