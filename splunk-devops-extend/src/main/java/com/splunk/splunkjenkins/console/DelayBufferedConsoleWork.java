package com.splunk.splunkjenkins.console;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Extension
public class DelayBufferedConsoleWork extends AsyncPeriodicWork {
    // not shorter than 15s, default to 1 min 30s
    private long period = TimeUnit.SECONDS.toMillis(Math.max(15, Long.getLong(DelayBufferedConsoleWork.class.getName(), 90)));

    public DelayBufferedConsoleWork() {
        super("Flush cached splunk console log");
    }

    @Override
    protected void execute(TaskListener taskListener) throws IOException, InterruptedException {
        ConsoleRecordCacheUtils.flushLog();
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
