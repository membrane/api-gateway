package com.predic8.membrane.core.util;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages periodic tasks with a single timer.
 */
public class TimerManager {
    protected java.util.Timer timer = new Timer();

    public void schedulePeriodicTask(TimerTask task, long period, String title) {
        timer.schedule(task, period, period);
    }

    public void schedule(TimerTask task, long delay, String title) {
        timer.schedule(task, delay);
    }

    public void shutdown() {
        timer.cancel();
    }
}
