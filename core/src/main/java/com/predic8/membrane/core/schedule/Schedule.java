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

package com.predic8.membrane.core.schedule;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.proxies.InternalProxy;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.predic8.membrane.core.interceptor.RuleMatchingInterceptor.assignRule;
import static com.predic8.membrane.core.util.text.TextUtil.escapeControlCharsForLog;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @description Calls the internal API periodically, for example to refresh a token, warm up a cache or send a
 * heartbeat every ten minutes. On every run Membrane sends a GET request to / through the flow and the target of
 * the API, just like a request that came in over HTTP. The first run starts one interval after startup. A run does
 * not start before the previous one has finished, and a run that takes longer than the timeout is interrupted. A
 * failed run is logged and not repeated before the next interval.
 * <p>Every Membrane instance runs its own schedules. If a job should run only once in a cluster, trigger an API
 * from outside instead, for example with a Kubernetes CronJob.</p>
 * @yaml
 * <pre><code>
 * internal:
 *   name: refresh-token
 *   schedule:
 *     interval: PT10M
 *   flow:
 *     - log: {}
 *   target:
 *     url: https://auth.example.com/token/refresh
 * </code></pre>
 */
@MCElement(name = "schedule", component = false)
public class Schedule {

    private static final Logger log = LoggerFactory.getLogger(Schedule.class);

    private Duration interval;
    private Duration timeout;

    /**
     * Guarded by this. A retried initialization of the API calls {@link #start} again on a clone that shares this
     * object, so starting replaces a running scheduler.
     * <p>Deliberately not the router's shared {@link com.predic8.membrane.core.util.TimerManager}: the flow of the API
     * can do anything, and a slow or failing run must not delay or cancel the other tasks of the router.</p>
     */
    private ScheduledExecutorService executor;

    public synchronized void start(final Router router, final InternalProxy api) {
        final var timeout = getTimeoutDuration();
        if (interval.toMillis() <= 0)
            throw new ConfigurationException("schedule: 'interval' must be at least one millisecond, but is %s.".formatted(interval));
        if (timeout.toMillis() <= 0)
            throw new ConfigurationException("schedule: 'timeout' must be at least one millisecond, but is %s.".formatted(timeout));

        stop();
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("schedule " + api.getName()).factory());
        // Fixed delay: a slow run delays the next one instead of overlapping with it.
        executor.scheduleWithFixedDelay(() -> run(router, api, timeout), interval.toMillis(), interval.toMillis(), MILLISECONDS);
        log.info("Calling internal API {} every {}.", api.getName(), interval);
    }

    public synchronized void stop() {
        if (executor != null)
            executor.shutdownNow();
        executor = null;
    }

    /**
     * Runs the call on its own virtual thread, so that it can be interrupted after the timeout. A virtual thread
     * blocked on a socket closes the socket when it is interrupted, so the call ends with an error response.
     */
    private void run(final Router router, final InternalProxy api, final Duration timeout) {
        if (!api.isActive()) {
            log.warn("Scheduled call skipped: internal API {} is not active.", api.getName());
            return;
        }
        final var worker = Thread.ofVirtual().name("schedule call " + api.getName()).start(() -> call(router, api));
        try {
            if (!worker.join(timeout)) {
                log.warn("Scheduled call of {} did not finish within {}. Interrupting it.", api.getName(), timeout);
                worker.interrupt();
            }
        } catch (InterruptedException e) {
            // The schedule was stopped.
            worker.interrupt();
        }
    }

    /**
     * Catches every exception, so that it is logged.
     */
    private void call(final Router router, final InternalProxy api) {
        try {
            final var exc = new Request.Builder().get("/").buildExchange();
            assignRule(exc, api);
            router.getFlowController().invokeRequestHandlers(exc, router.getTransport().getFlow());

            final var response = exc.getResponse();
            if (response == null) {
                log.warn("Scheduled call of {} got no response.", api.getName());
                exc.finishExchange(true, "no response");
                return;
            }
            // Read the body, so the connection to the target goes back to the pool.
            response.discardBody();
            final var status = response.getStatusCode();
            if (status >= 400) {
                log.warn("Scheduled call of {} failed with status {}.", api.getName(), status);
                exc.finishExchange(true, "status " + status);
                return;
            }
            log.debug("Scheduled call of {} succeeded with status {}.", api.getName(), status);
            exc.setCompleted();
        } catch (Exception e) {
            log.warn("Scheduled call of {} failed: {}", api.getName(), escapeControlCharsForLog(e.getMessage()));
            log.debug("Stack trace of the failed scheduled call of {}:", api.getName(), e);
        }
    }

    private Duration getTimeoutDuration() {
        return timeout == null ? interval : timeout;
    }

    public String getInterval() {
        return interval == null ? null : interval.toString();
    }

    /**
     * @description Time between two calls in the <i>ISO 8601 Duration</i> format, e.g. PT30S for 30 seconds, PT10M
     * for 10 minutes or PT1H for one hour. The first call starts one interval after startup. The next call starts one
     * interval after the previous one has finished.
     * @example PT10M
     */
    @MCAttribute
    @Required
    public void setInterval(String interval) {
        this.interval = parse("interval", interval);
    }

    public String getTimeout() {
        return timeout == null ? null : timeout.toString();
    }

    /**
     * @description Maximum time a call may take, in the <i>ISO 8601 Duration</i> format. A call that takes longer is
     * interrupted, e.g. when the target accepts the connection but never answers.
     * @default the interval
     * @example PT30S
     */
    @MCAttribute
    public void setTimeout(String timeout) {
        this.timeout = parse("timeout", timeout);
    }

    private static Duration parse(final String attribute, final String duration) {
        try {
            return Duration.parse(duration);
        } catch (DateTimeParseException e) {
            throw new ConfigurationException("schedule: '%s' %s is not an ISO 8601 duration like PT10M.".formatted(attribute, duration), e);
        }
    }
}
