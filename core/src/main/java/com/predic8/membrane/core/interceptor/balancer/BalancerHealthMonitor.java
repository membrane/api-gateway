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
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.context.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.*;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.*;
import static com.predic8.membrane.core.util.ExceptionUtil.concatMessageAndCauseMessages;

/**
 * @description Health monitor for a {@link LoadBalancingInterceptor} {@link Cluster}.
 * Periodically checks the health of all clusters registered
 * on the router and updates each {@link Node}'s status accordingly.
 * When initialized, it schedules a task to call each {@link Node}'s health
 * endpoint and marks nodes as {@link Status#UP} or {@link Status#DOWN} based on the HTTP response.
 * This ensures the load balancer always has up-to-date status for routing decisions.
 * @example <a href="https://github.com/membrane/api-gateway/tree/master/distribution/examples/loadbalancing/6-health-monitor">health monitor example</a>
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name = "balancerHealthMonitor")
public class BalancerHealthMonitor implements ApplicationContextAware, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BalancerHealthMonitor.class);
    public static final String BALANCER_HEALTH_MONITOR = "balancer-health-monitor";

    /**
     * Initial delay of 5s
     */
    public static final int INITIAL_DELAY = 5000;

    private Router router;
    private int interval = 10000;
    private HttpClient client;

    private HttpClientConfiguration httpClientConfig = new HttpClientConfiguration();

    private volatile boolean stopped;

    private void init() {
        if (interval <= 0)
            throw new ConfigurationException("balancerHealthMonitor: 'interval' (ms) must be > 0");

        log.info("Starting HealthMonitor for load balancing with interval of {} ms", interval);

        httpClientConfig.setMaxRetries(0); // Health check should never be retried.
        client = router.getHttpClientFactory().createClient(httpClientConfig);
        createScheduler();

        ConnectionConfiguration cc = httpClientConfig.getConnection();
        int soTimeout = cc.getSoTimeout();
        int timeout = cc.getTimeout();
        if (soTimeout > 10_000) {
            log.warn("Socket timeout is {} s. Keep timeout low to prevent the health monitor thread from hanging!", soTimeout / 1000);
        }
        if (timeout > 10_000) {
            log.warn("Connection timeout is {} s. Keep timeout low to prevent the health monitor thread from hanging!", timeout / 1000);
        }
    }

    private final Runnable healthCheckTask = () -> {
        log.debug("Starting health check.");
        collectClusters(router).forEach(cluster -> {

            // Positioned here because the loop may run for several seconds if the list is big
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

    private void createScheduler() {
        router.getTimerManager().schedulePeriodicTask(new TimerTask() {
            @Override
            public void run() {
                healthCheckTask.run();
            }
        }, INITIAL_DELAY, BALANCER_HEALTH_MONITOR);
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
        if (exc.getResponse() == null)
            return DOWN;
        try {
            exc.getResponse().getBody().read();
        } catch (IOException e) {
            log.debug("Calling health endpoint failed: {} {}", exc, concatMessageAndCauseMessages(e));
            return DOWN;
        }
        int status = exc.getResponse().getStatusCode();
        if (status >= 300) {
            log.warn("Node {}:{} health check failed with HTTP {} status code", node.getHost(), node.getPort(), status);
            return DOWN;
        }
        log.debug("Node {}:{} is healthy (HTTP {})", node.getHost(), node.getPort(), status);
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

    @Override
    public void destroy() {
        stopped = true;
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.debug("Closing HttpClient failed: {}", e.getMessage());
            }
        }
    }

    /**
     * @param interval the interval between health checks, in milliseconds
     * @description Sets the health check interval (in milliseconds).
     * @example 30000
     * @default 10000
     */
    @MCAttribute
    public void setInterval(int interval) {
        this.interval = interval;
    }

    /**
     * @return current health check interval in milliseconds
     */
    public int getInterval() {
        return interval;
    }

    /**
     * @return the HTTP client configuration used to construct the {@link HttpClient}
     */
    public HttpClientConfiguration getHttpClientConfig() {
        return httpClientConfig;
    }

    /**
     * @description Optional HTTP client configuration for health probes (e.g., timeouts, TLS).
     * If provided, it is used when creating the {@link HttpClient} via the router's client factory.
     * @see Router#getHttpClientFactory()
     * @see HttpClientConfiguration
     */
    @MCChildElement
    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
    }

}
