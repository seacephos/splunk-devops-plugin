package com.splunk.splunkjenkins.console;

import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

import javax.annotation.Nonnull;
import java.io.OutputStream;

public class SplunkConsoleTaskListenerDecorator extends TaskListenerDecorator {
    transient PipelineConsoleDecoder decoder;
    String source;

    public SplunkConsoleTaskListenerDecorator(WorkflowRun run) {
        this.decoder = new PipelineConsoleDecoder(run);
        this.source = run.getUrl() + "console";
    }

    @Nonnull
    @Override
    public OutputStream decorate(@Nonnull OutputStream outputStream) {
        if (decoder == null) {
            //resume from restart
            decoder = new PipelineConsoleDecoder(null);
        }
        //called for every step 
        return new LabelConsoleLineStream(outputStream, source, decoder);
    }
}
