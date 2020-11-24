package com.splunk.splunkjenkins.listeners;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.splunk.splunkjenkins.Constants;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.model.EventType.QUEUE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getMasterStats;

/**
 * <pre>{@code from jenkins javadoc
 *  (enter) --> waitingList --+--> blockedProjects
 *                            |        ^
 *                            |        |
 *                            |        v
 *                            +--> buildables ---> pending ---> left
 *                                     ^              |
 *                                     |              |
 *                                     +---(rarely)---+
 *
 * }</pre>
 */
@SuppressWarnings("unused")
@Extension
public class LoggingQueueListener extends QueueListener {
    // Cache to keep track of queue time
    private final static Cache<Long, Float> cache = CacheBuilder.newBuilder()
            .maximumSize(3000).build();
    //To keep track of blocked events timestamp
    private final static ConcurrentHashMap<Long, Long> blockedQueue = new ConcurrentHashMap<Long, Long>();
    //To keep track of buildable events timestamp
    private final static ConcurrentHashMap<Long, Long> buildableQueue = new ConcurrentHashMap<Long, Long>();
    //To keep track of waiting events timestamp
    private final static ConcurrentHashMap<Long, Long> waitingQueue = new ConcurrentHashMap<Long, Long>();

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        sendToSplunkOnEnter(wi.task, wi.getId(), waitingQueue, Constants.ENQUEUE_TAG_NAME);
    }

    @Override
    public void onLeaveWaiting(Queue.WaitingItem wi) {
        sendToSplunkOnLeft(wi.task, wi.getId(), waitingQueue,
                Constants.WAITING_PHASE_NAME, wi.getWhy(), wi.getInQueueSince());
    }

    @Override
    public void onEnterBlocked(Queue.BlockedItem bi) {
        sendToSplunkOnEnter(bi.task, bi.getId(), blockedQueue, Constants.BLOCKED_PHASE_NAME);
    }

    @Override
    public void onLeaveBlocked(Queue.BlockedItem bi) {
        sendToSplunkOnLeft(bi.task, bi.getId(), blockedQueue,
                Constants.BLOCKED_PHASE_NAME, bi.getWhy(), bi.getInQueueSince());
    }

    @Override
    public void onEnterBuildable(Queue.BuildableItem bi) {
        sendToSplunkOnEnter(bi.task, bi.getId(), buildableQueue, Constants.BUILDABLE_PHASE_NAME);

    }

    @Override
    public void onLeaveBuildable(Queue.BuildableItem bi) {
        sendToSplunkOnLeft(bi.task, bi.getId(), buildableQueue,
                Constants.BUILDABLE_PHASE_NAME, bi.getWhy(), bi.getInQueueSince());
    }

    @Override
    public void onLeft(Queue.LeftItem li) {
        Float queueTime = sendToSplunkOnLeft(li.task, li.getId(), buildableQueue,
                Constants.DEQUEUE_TAG_NAME, li.getWhy(), li.getInQueueSince());
        //Storing it in the cache to access it later
        cache.put(li.getId(), queueTime);

    }

    /**
     * queue task only have project name, don't have build number
     *
     * @param task Queue task
     * @return task name
     */
    public String getTaskName(Queue.Task task) {
        if (task == null) {
            return "n/a";
        } else {
            return task.getUrl();
        }
    }

    public static Float getQueueTime(Long Id) {
        Float queueTime = cache.getIfPresent(Id);
        if (queueTime == null) {
            //the queue has been garbage collected
            queueTime = 0f;
        }
        return queueTime;
    }

    /**
     * Generate common metadata to be reported across all the different phases of the queue
     *
     * @param type     type of the queue phase
     * @param id       identifier of the queue
     * @param itemName job name associated with the queue
     * @return generated events to be published to splunk
     */
    private Map getCommonEvents(String type, Long id, String itemName) {
        Map event = getMasterStats();
        event.put("type", type);
        event.put(Constants.TAG, Constants.QUEUE_TAG_NAME);
        event.put("queue_id", id);
        event.put("item", itemName);
        return event;
    }

    /**
     * Send build queue meta data to splunk on entering to any of the queue phases
     * like buildable, blocked and waiting
     *
     * @param task task associated with a queue
     * @param id id of the queue
     * @param queue map used to store the time stamp of the queue phases
     * @param eventType type of the queue phase
     */
    private void sendToSplunkOnEnter(Queue.Task task, Long id, ConcurrentHashMap queue, String eventType) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(QUEUE_INFO)) {
            return;
        }
        String name = getTaskName(task);
        if (SplunkJenkinsInstallation.get().isJobIgnored(name)) {
            return;
        }
        queue.put(id, System.currentTimeMillis());
        String eventTypeEnqueue;
        if(eventType == Constants.ENQUEUE_TAG_NAME) {
            eventTypeEnqueue = eventType;
        } else {
            eventTypeEnqueue = Constants.ENQUEUE_TAG_NAME + "_" + eventType;
        }
        Map event = getCommonEvents(eventTypeEnqueue, id, name);
        SplunkLogService.getInstance().send(event, QUEUE_INFO);

    }

    /**
     * Send queue time and other meta data to splunk on leaving from any of the
     * queue phases like buildable, blocked and waiting
     *
     * @param task task associated with a queue
     * @param id id of the queue
     * @param queue map used to store the time stamp of the queue phases
     * @param eventType type of the queue phase
     * @param message message of the slave available in the queue
     * @param inQueueSince time
     * @return
     */
    private Float sendToSplunkOnLeft(Queue.Task task, Long id, ConcurrentHashMap queue, String eventType, String message, Long inQueueSince) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(QUEUE_INFO)) {
            return 0f;
        }
        String name = getTaskName(task);
        if (SplunkJenkinsInstallation.get().isJobIgnored(name)) {
            return 0f;
        }

        float queueTimeDuration;
        String eventTypeDequeue;
        String durationName;
        //Customize queue time for phases Vs on left at last
        if(eventType == Constants.DEQUEUE_TAG_NAME) {
            eventTypeDequeue = eventType;
            durationName = "queue_time";
            //Calculate queue time for on left from getInQueueSince available in the queueItem
            queueTimeDuration = getQueueTime(id, inQueueSince);
        } else {
            eventTypeDequeue = Constants.DEQUEUE_TAG_NAME + "_" + eventType;
            durationName = eventType + "_time";
            //Calculate duration for the phase from the stored HashMap in this object.
            queueTimeDuration = getDurationInQueuePhase(id, queue);
        }

        Map event = getCommonEvents(eventTypeDequeue, id, name);
        event.put(durationName, queueTimeDuration);
        event.put("message", message);

        SplunkLogService.getInstance().send(event, QUEUE_INFO);
        return queueTimeDuration;
    }

    /**
     * Calculate the time spent in a particular phase
     * @param id id of the item in queue
     * @param queueType type of queue
     * @return time duration spent in a particular phase
     */
    private Float  getDurationInQueuePhase(Long id, Map<Long, Long> queueType) {
        Long startTime = queueType.get(id);
        Float durationInPhase;
        if (startTime == null) {
            //the queue has been garbage collected or jenkins has restarted
            durationInPhase = 0f;
        } else {
            durationInPhase = (System.currentTimeMillis() - startTime)/1000f;
            queueType.remove(id);
        }
        return durationInPhase;
    }

    private Float getQueueTime(Long id, Long inqueueSince) {
        float queueTime = (System.currentTimeMillis() - inqueueSince) / 1000f;
        return queueTime;
    }

    public static void expire(Long Id) {
        cache.invalidate(Id);
    }

}
