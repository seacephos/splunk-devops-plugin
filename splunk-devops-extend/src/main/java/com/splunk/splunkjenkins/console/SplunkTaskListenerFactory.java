package com.splunk.splunkjenkins.console;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.model.EventRecord;
import com.splunk.splunkjenkins.model.EventType;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.Queue;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.model.EventType.CONSOLE_LOG;

@Extension
public class SplunkTaskListenerFactory implements TaskListenerDecorator.Factory {
    private static final Logger LOGGER = Logger.getLogger(SplunkConsoleTaskListenerDecorator.class.getName());
    private static final int CACHED_LINES_LIMIT = 200;
    private static final ConcurrentLinkedQueue<EventRecord> consoleQueue = new ConcurrentLinkedQueue<>();
    private static final LoadingCache<WorkflowRun, SplunkConsoleTaskListenerDecorator> cachedDecorator = CacheBuilder.newBuilder()
            .weakKeys()
            .maximumSize(1024)
            .build(new CacheLoader<WorkflowRun, SplunkConsoleTaskListenerDecorator>() {
                @Override
                public SplunkConsoleTaskListenerDecorator load(WorkflowRun key) {
                    return new SplunkConsoleTaskListenerDecorator(key);
                }
            });

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
                if (SplunkJenkinsInstallation.get().isJobIgnored(run.getUrl())) {
                    return null;
                }
                return cachedDecorator.get(run);
            }
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        } catch (ExecutionException e) {
            LOGGER.finer("failed to load cached decorator");
        }
        return null;
    }

    public static void enqueue(EventRecord record) {
        boolean added = consoleQueue.add(record);
        if (!added) {
            LOGGER.warning("failed to add log " + record.getMessageString());
        } else if (consoleQueue.size() > CACHED_LINES_LIMIT) {
            flushLog();
        }
    }

    public static void flushLog() {
        EventRecord record;
        List<EventRecord> pendingRecords = new ArrayList<>();
        try {
            while ((record = consoleQueue.poll()) != null) {
                pendingRecords.add(record);
            }
            SplunkLogService.getInstance().sendBatch(pendingRecords, EventType.CONSOLE_LOG);
        } catch (Throwable ex) {
            LOGGER.log(Level.SEVERE, "flush log error", ex);
        }
    }

    public static void removeCache(WorkflowRun run) {
        cachedDecorator.invalidate(run);
    }
}
