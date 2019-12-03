package com.splunk.splunkjenkins.console;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Extension
public class DelayBufferedConsoleWork extends AsyncPeriodicWork {
    // not shorter than 2 min, default to 3 min
    private long period = TimeUnit.MINUTES.toMillis(Math.max(2, Long.getLong(DelayBufferedConsoleWork.class.getName(), 3)));

    public DelayBufferedConsoleWork() {
        super("Flush cached splunk console log");
    }

    @Override
    protected void execute(TaskListener taskListener) throws IOException, InterruptedException {
        SplunkTaskListenerFactory.flushLog();
    }

    @Override
    public long getRecurrencePeriod() {
        return period;
    }

    @Override
    protected Level getNormalLoggingLevel() {
        return Level.FINE;
    }
}
