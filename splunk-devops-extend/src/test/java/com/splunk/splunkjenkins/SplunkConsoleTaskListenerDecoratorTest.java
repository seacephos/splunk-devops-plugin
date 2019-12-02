package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.console.SplunkTaskListenerFactory;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.UUID;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SplunkConsoleTaskListenerDecoratorTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    String id = UUID.randomUUID().toString();
    @Rule
    public JenkinsRule r = new JenkinsRule();
    private String jobScript = "node{\n" +
            "  parallel first: {sh \"echo SplunkConsoleTaskListenerDecoratorTest\"},\n" +
            "          second: {sh \"echo " + id + "\"}\n" +
            "  echo '" + id + "'" +
            " }";

    @Before
    public void setUpToken() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
        SplunkJenkinsInstallation.get().setGlobalPipelineFilter(true);
    }

    @After
    public void tearDown() {
        SplunkJenkinsInstallation.get().setGlobalPipelineFilter(false);
    }

    @Test
    public void testSendConsole() throws Exception {
        long startTime = System.currentTimeMillis();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "listener_test");
        p.setDefinition(new CpsFlowDefinition(jobScript, false));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse(b1.isBuilding());
        r.assertLogContains("SplunkConsoleTaskListenerDecoratorTest", b1);
        assertTrue(b1.getDuration() > 0);
        //manual flush
        SplunkTaskListenerFactory.flushLog();
        //check log
        verifySplunkSearchResult("source=" + b1.getUrl() + "console " + id, startTime, 2);
        verifySplunkSearchResult("source=" + b1.getUrl() + "console label=first", startTime, 1);
    }
}
