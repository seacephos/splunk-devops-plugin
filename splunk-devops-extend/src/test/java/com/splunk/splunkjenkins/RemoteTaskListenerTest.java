package com.splunk.splunkjenkins;

import com.google.common.collect.ImmutableSet;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;


public class RemoteTaskListenerTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();
    String id = UUID.randomUUID().toString();
    private String jobScript = "sendSplunkConsoleLog {" +
            "  node('remote') {\n" +
            "    remoteLogCall('" + id + "')\n" +
            "  }" +
            "}";

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
    }

    @Test
    public void remoteLogTest() throws Exception {
        r.createSlave("remote", null, null);
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(jobScript, true));
        long startTime = System.currentTimeMillis();
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains(id, b);
        verifySplunkSearchResult("source=" + b.getUrl() + "console " + id, startTime, 1);
        verifySplunkSearchResult("source=" + b.getUrl() + "console master=false", startTime, 1);
    }

    public static final class RemoteLogStep extends Step {
        String id;

        @DataBoundConstructor
        public RemoteLogStep(String id) {
            this.id = id;
        }

        @Override
        public StepExecution start(StepContext context) throws Exception {
            return new Execution(context, this);
        }

        private static final class Execution extends SynchronousNonBlockingStepExecution<Void> {
            transient private RemoteLogStep step;

            Execution(StepContext context, RemoteLogStep step) {
                super(context);
                this.step = step;
            }

            @Override
            protected Void run() throws Exception {
                FilePath ws = getContext().get(FilePath.class);
                PrintCallable callable = new PrintCallable(getContext().get(TaskListener.class), step.id);
                return ws.act(callable);
            }
        }

        @TestExtension
        public static final class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return ImmutableSet.of(Node.class, TaskListener.class, FilePath.class);
            }

            @Override
            public String getFunctionName() {
                return "remoteLogCall";
            }
        }

        private static final class PrintCallable implements FilePath.FileCallable<Void>, Serializable {
            private final TaskListener listener;
            private String id;

            PrintCallable(TaskListener listener, String id) {
                this.listener = listener;
                this.id = id;
            }

            @Override
            public Void invoke(File file, VirtualChannel virtualChannel) throws IOException, InterruptedException {
                listener.getLogger().println("printed a message on master=" + JenkinsJVM.isJenkinsJVM());
                listener.getLogger().println("id=" + id);
                for (int i = 0; i < 3; i++) {
                    listener.getLogger().println("dummy line=" + i);
                }
                listener.getLogger().flush();
                return null;
            }

            @Override
            public void checkRoles(RoleChecker roleChecker) throws SecurityException {

            }
        }
    }
}
