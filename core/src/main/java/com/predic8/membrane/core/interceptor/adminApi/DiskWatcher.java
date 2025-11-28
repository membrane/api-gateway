/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.adminApi;

import tools.jackson.databind.core.JsonProcessingException;
import com.predic8.membrane.core.transport.ws.WebSocketConnectionCollection;
import com.predic8.membrane.core.util.TimerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.TimerTask;

import static com.google.common.collect.ImmutableMap.of;

public class DiskWatcher {

    private final static Logger LOG = LoggerFactory.getLogger(DiskWatcher.class.getName());

    private WebSocketConnectionCollection connections;
    private int intervalMilliseconds = 10000;

    public void init(TimerManager timerManager, WebSocketConnectionCollection connections) {
        this.connections = connections;
        timerManager.schedulePeriodicTask(new TimerTask() {
            @Override
            public void run() {
                getDiskStats();
            }
        }, intervalMilliseconds, "DiskWatcher");
    }

    private void getDiskStats() {
        try {
            connections.broadcast(of(
                    "subject", "metricUpdate",
                    "data", of(
                            "metrics", of(
                                    "totalDiskSpace", new File("/").getTotalSpace(),
                                    "freeDiskSpace", new File("/").getFreeSpace()
                            )
                    )
            ));
        } catch (JsonProcessingException e) {
            LOG.error("", e); // should not happen
        }
    }

    public void setIntervalMilliseconds(int intervalMilliseconds) {
        this.intervalMilliseconds = intervalMilliseconds;
    }
}
