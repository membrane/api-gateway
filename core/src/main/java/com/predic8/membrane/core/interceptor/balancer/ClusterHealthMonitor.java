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

package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.balancer.Node.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.Logger;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.context.*;

import java.util.concurrent.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.*;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.*;
import static java.lang.System.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * @description Health monitor for a load balancing cluster.
 * Periodically checks the health of all clusters registered
 * on the router and updates each node status accordingly.
 * When initialized, it schedules a task to call each node's health
 * endpoint and marks nodes as UP or DOWN based on the HTTP response.
 * This ensures the load balancer always has up-to-date status for routing decisions.
 * @example examples/loadbalancing/7-tls
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name = "balancerHealthMonitor")
public class ClusterHealthMonitor implements ApplicationContextAware, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ClusterHealthMonitor.class);

    private Router router;
    private int interval = 10;
    private ScheduledExecutorService scheduler;
    private HttpClient client;

    private HttpClientConfiguration httpClientConfig;

    private volatile boolean stopped;

    private void init() {
        if (interval <= 0)
            throw new ConfigurationException("lbClusterHealthMonitor: 'interval' must be > 0");

        log.info("Starting HealthMonitor for load balancing with interval of {} seconds", interval);

        scheduler = createSheduler();
        client = router.getHttpClientFactory().createClient(httpClientConfig);
    }

    private final Runnable healthCheckTask = () -> {
        log.debug("Starting health check.");
        collectClusters(router).forEach(cluster -> {

            // Positioned here cause the loop my run for several seconds if the list is big
            if (stopped)
                return;

            log.debug("Checking cluster '{}'", cluster.getName());
            cluster.getNodes().forEach(this::updateStatus);
        });
        log.debug("Health check complete.");
    };

    private void updateStatus(Node node) {
        node.setStatus(isHealthy(node));
    }

    private ScheduledExecutorService createSheduler() {
        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor();
        s.scheduleAtFixedRate(
                () -> new Thread(healthCheckTask, "HealthCheckThread").start(),
                interval, interval, SECONDS
        );
        return s;
    }

    private Status isHealthy(Node node) {
        String url = getNodeHealthEndpoint(node);
        try {
            return getStatus(node, doCall(url));
        } catch (Exception e) {
            log.warn("Calling health endpoint failed: {}, {}", url, e.getMessage());
            return DOWN;
        }
    }

    private static Status getStatus(Node node, Exchange exc) {
        int status = exc.getResponse().getStatusCode();
        if (status >= 300) {
            log.warn("Node {}:{} health check failed with HTTP {}", node.getHost(), node.getPort(), status);
            return DOWN;
        }
        log.debug("Node {}:{} is healthy (HTTP {})", node.getHost(), node.getPort(), status);
        if (node.isDown())
            node.setLastUpTime(currentTimeMillis());
        return UP;
    }

    private static String getNodeHealthEndpoint(Node node) {
        return node.getHealthUrl() != null
                ? node.getHealthUrl()
                : getUrl(node.getHost(), node.getPort());
    }

    private Exchange doCall(String url) throws Exception {
        Exchange exc = get(url).buildExchange();
        client.call(exc);
        return exc;
    }

    private static @NotNull String getUrl(String host, int port) {
        return "http://" + host + ":" + port + "/";
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.router = applicationContext.getBean(Router.class);
    }

    @Override
    public void afterPropertiesSet() {
        init();
    }

    public void shutdown() {

        stopped = true;

        out.println("???????????????????????");
        if (scheduler == null)
            return;

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, SECONDS)) {
                log.warn("Health check scheduler did not terminate gracefully, forcing shutdown");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for scheduler shutdown");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

    }

    public void stop() {
        out.println("********************************************");
        scheduler.shutdownNow();               // interrupts running task, returns immediately
    }

    /**
     * @param interval the interval between health checks, in seconds
     * @description Sets the health check interval (in seconds).
     * @example 30
     * @default 10
     */
    @MCAttribute
    public void setInterval(int interval) {
        this.interval = interval;
    }

    public Integer getInterval() {
        return interval;
    }

    public HttpClientConfiguration getHttpClientConfig() {
        return httpClientConfig;
    }

    /**
     * TODO
     *
     * @param httpClientConfig
     */
    @MCChildElement
    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
    }

    @Override
    public void destroy() throws Exception {
        shutdown();
    }
}
