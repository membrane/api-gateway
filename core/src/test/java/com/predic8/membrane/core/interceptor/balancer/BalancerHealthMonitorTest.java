/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.beanregistry.BeanRegistry;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.TimerManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TimerTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BalancerHealthMonitorTest {

    @Test
    void healthChecksAreScheduledWithConfiguredInterval() {
        final var timerManager = new RecordingTimerManager();
        final var monitor = createMonitor(timerManager);
        monitor.setInterval(60_000);

        monitor.afterPropertiesSet();
        try {
            assertEquals(List.of(60_000L), timerManager.periods);
        } finally {
            monitor.destroy();
        }
    }

    private static BalancerHealthMonitor createMonitor(TimerManager timerManager) {
        final var router = new DummyTestRouter() {
            @Override
            public TimerManager getTimerManager() {
                return timerManager;
            }
        };
        final var registry = mock(BeanRegistry.class);
        when(registry.getBean(Router.class)).thenReturn(Optional.of(router));

        final var monitor = new BalancerHealthMonitor();
        monitor.setRegistry(registry);
        return monitor;
    }

    /**
     * Records the requested periods instead of scheduling, so no health check runs.
     */
    private static class RecordingTimerManager extends TimerManager {

        final List<Long> periods = new ArrayList<>();

        @Override
        public void schedulePeriodicTask(TimerTask task, long period, String title) {
            periods.add(period);
        }
    }
}
