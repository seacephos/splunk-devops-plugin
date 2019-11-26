package com.splunk.splunkjenkins;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class DelayBufferedConsoleWork extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(DelayBufferedConsoleWork.class.getName());

    private long period = TimeUnit.MINUTES.toMillis(Math.max(3, Long.getLong(DelayBufferedConsoleWork.class.getName(), 5)));

    public DelayBufferedConsoleWork() {
        super("Flush cached splunk console log");
    }

    @Override
    protected void execute(TaskListener taskListener) throws IOException, InterruptedException {
        DelayConsoleLineStream.flushLog();
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
