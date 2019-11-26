package com.splunk.splunkjenkins;

import hudson.Extension;
import hudson.model.Queue;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.model.EventType.CONSOLE_LOG;

public class SplunkConsoleTaskListenerDecorator extends TaskListenerDecorator {
    private static final Logger LOGGER = Logger.getLogger(SplunkConsoleTaskListenerDecorator.class.getName());
    private String source;

    public SplunkConsoleTaskListenerDecorator(String source) {
        this.source = source;
    }

    @Nonnull
    @Override
    public OutputStream decorate(@Nonnull OutputStream outputStream) throws IOException, InterruptedException {
        return new DelayConsoleLineStream(outputStream, source);
    }


    @Extension
    public static class SplunkTaskListenerFactory implements TaskListenerDecorator.Factory {
        @CheckForNull
        @Override
        public TaskListenerDecorator of(@Nonnull FlowExecutionOwner flowExecutionOwner) {
            if (!SplunkJenkinsInstallation.get().isPipelineFilterEnabled()) {
                return null;
            }
            if (SplunkJenkinsInstallation.get().isEventDisabled(CONSOLE_LOG)) {
                return null;
            }
            try {
                Queue.Executable executable = flowExecutionOwner.getExecutable();
                if (executable instanceof WorkflowRun) {
                    WorkflowRun run = (WorkflowRun) executable;
                    String source = run.getUrl() + "console";
                    if (SplunkJenkinsInstallation.get().isJobIgnored(source)) {
                        return null;
                    }
                    return new SplunkConsoleTaskListenerDecorator(source);
                }
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
            return null;
        }
    }

}
