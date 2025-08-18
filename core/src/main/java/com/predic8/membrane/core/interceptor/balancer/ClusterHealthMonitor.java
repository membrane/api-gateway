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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.balancer.Node.Status;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.collectClusters;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.DOWN;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.UP;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @description Health monitor for a {@link LoadBalancingInterceptor} {@link Cluster}.
 * Periodically checks the health of all clusters registered
 * on the router and updates each {@link Node}'s status accordingly.
 * When initialized, it schedules a task to call each {@link Node}'s health
 * endpoint and marks nodes as {@link Status#UP} or {@link Status#DOWN} based on the HTTP response.
 * This ensures the load balancer always has up-to-date status for routing decisions.
 * @example <a href="https://github.com/membrane/api-gateway/tree/master/distribution/examples/loadbalancing/7-tls">tls example</a>
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name = "balancerHealthMonitor")
public class ClusterHealthMonitor implements ApplicationContextAware, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ClusterHealthMonitor.class);

    private Router router;
    private int interval = 10000;
    private ScheduledExecutorService scheduler;
    private HttpClient client;

    private HttpClientConfiguration httpClientConfig;

    private volatile boolean stopped;

    private void init() {
        if (interval <= 0)
            throw new ConfigurationException("lbClusterHealthMonitor: 'interval' (ms) must be > 0");

        log.info("Starting HealthMonitor for load balancing with interval of {} ms", interval);

        scheduler = createScheduler();
        client = router.getHttpClientFactory().createClient(httpClientConfig);
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

    private ScheduledExecutorService createScheduler() {
        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor();
        s.scheduleAtFixedRate(healthCheckTask, interval, interval, MILLISECONDS);
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

    @Override
    public void destroy() {
        stopped = true;

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
     * */
    public Integer getInterval() {
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
     *
     * @see Router#getHttpClientFactory()
     * @see HttpClientConfiguration
     */
    @MCChildElement
    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
    }

}
