package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class RemoveAsReadMessage {
    private int id;
    private long randomId = -1; // from encrypted dialogs
    private long readTime = -1;
    private int scheduledTimeMs;

    public RemoveAsReadMessage() {
    }

    public RemoveAsReadMessage(int id, long randomId, int scheduledTimeMs) {
        this.id = id;
        this.randomId = randomId;
        this.scheduledTimeMs = scheduledTimeMs;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getRandomId() {
        return randomId;
    }

    public int getScheduledTimeMs() {
        return scheduledTimeMs;
    }

    @JsonIgnore
    public boolean isRead() {
        return readTime != -1;
    }

    public void setReadTime(long readTime) {
        this.readTime = readTime;
    }

    public int calculateRemainingDelay() {
        long remainingDelay = readTime + scheduledTimeMs - System.currentTimeMillis();
        return Math.max((int)remainingDelay, 0);
    }
}
