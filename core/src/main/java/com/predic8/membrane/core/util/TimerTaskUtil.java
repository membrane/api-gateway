package com.predic8.membrane.core.util;

import java.util.TimerTask;

public class TimerTaskUtil {
    public static TimerTask createTimerTask(Runnable task) {
        return new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        };
    }
}
