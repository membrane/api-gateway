package com.predic8.membrane.core.interceptor.adminApi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.predic8.membrane.core.transport.ws.WebSocketConnectionCollection;
import com.predic8.membrane.core.util.TimerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimerTask;

import static com.google.common.collect.ImmutableMap.of;

public class MemoryWatcher {

    private final static Logger LOG = LoggerFactory.getLogger(MemoryWatcher.class.getName());

    private WebSocketConnectionCollection connections;
    private int intervalMilliseconds = 10000;

    public void init(TimerManager timerManager, WebSocketConnectionCollection connections) {
        this.connections = connections;
        timerManager.schedulePeriodicTask(new TimerTask() {
            @Override
            public void run() {
                getMemoryStats();
            }
        }, intervalMilliseconds, "DiskWatcher");
    }

    private void getMemoryStats() {
        try {
            connections.broadcast(of(
                    "type", "MemoryStats",
                    "memory", of(
                            "total", Runtime.getRuntime().totalMemory(),
                            "free", Runtime.getRuntime().freeMemory())));
        } catch (JsonProcessingException e) {
            LOG.error("", e); // should not happen
        }
    }

    public void setIntervalMilliseconds(int intervalMilliseconds) {
        this.intervalMilliseconds = intervalMilliseconds;
    }
}
