package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.model.EventRecord;
import com.splunk.splunkjenkins.model.EventType;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.console.ConsoleNote;
import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.model.EventType.CONSOLE_LOG;

public class DelayConsoleLineStream extends LineTransformationOutputStream {
    private static final Logger LOGGER = Logger.getLogger(DelayConsoleLineStream.class.getName());
    private static final ConcurrentLinkedQueue<EventRecord> consoleQueue = new ConcurrentLinkedQueue<>();
    protected final OutputStream out;
    private String source;

    public DelayConsoleLineStream(OutputStream out, String source) {
        this.out = out;
        this.source = source;
    }

    @Override
    protected void eol(byte[] bytes, int len) throws IOException {
        out.write(bytes, 0, len);
        String line = new String(bytes, 0, len, "UTF-8");
        line = ConsoleNote.removeNotes(line);
        EventRecord record = new EventRecord(line, CONSOLE_LOG);
        record.setSource(source);
        enqueue(record);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
        super.flush();
        flushLog();
    }

    @Override
    public void close() throws IOException {
        this.out.close();
        super.close();
    }

    public static void enqueue(EventRecord record) {
        boolean added = consoleQueue.add(record);
        if (!added) {
            LOGGER.warning("failed to add log " + record.getMessageString());
        } else if (consoleQueue.size() > 100) {
            flushLog();
        }
    }

    public static void flushLog() {
        EventRecord record;
        List<EventRecord> pendingRecords = new ArrayList<>();
        while ((record = consoleQueue.poll()) != null) {
            pendingRecords.add(record);
        }
        SplunkLogService.getInstance().sendBatch(pendingRecords, EventType.CONSOLE_LOG);
    }
}
