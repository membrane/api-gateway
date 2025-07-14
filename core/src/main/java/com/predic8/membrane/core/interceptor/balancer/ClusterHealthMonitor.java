package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.collectClusters;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.DOWN;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.UP;

/**
 * @description Configuration element for scheduling periodic cluster health checks.
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name = "lbClusterHeathMonitor")
public class ClusterHealthMonitor implements ApplicationContextAware, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ClusterHealthMonitor.class);

    private Router router;
    private int interval;
    private ScheduledExecutorService scheduler;
    private static final HttpClient client = new HttpClient();

    private final Runnable healthCheckTask = () -> {
        log.info("Starting Health Check");
        List<Cluster> clusters = collectClusters(router);
        clusters.forEach(cluster -> {
            log.info("Checking cluster '{}'", cluster.getName());
            cluster.getNodes().forEach(node -> {
                node.setStatus(isHealthy(node));
            });
        });
        log.info("Health Check complete");
    };

    private Node.Status isHealthy(Node node) {
        String url = node.getHealthUrl() != null
                ? node.getHealthUrl()
                : getUrl(node.getHost(), node.getPort());

        Exchange exc;
        try {
            exc = doCall(url);
        } catch (Exception e) {
            log.error("Error Calling: {}, {}", url, e);
            throw new RuntimeException(e);
        }

        try {
            int status = exc.getResponse().getStatusCode();
            if (status >= 500) {
                log.error("Node {}:{} health check failed with HTTP {}", node.getHost(), node.getPort(), status);
                return DOWN;
            } else {
                log.info("Node {}:{} is healthy (HTTP {})", node.getHost(), node.getPort(), status);
                return UP;
            }
        } catch (Exception e) {
            log.error("Unexpected error during health check for node {}:{} - marking DOWN", node.getHost(), node.getPort(), e);
        }

        return DOWN;
    }

    private Exchange doCall(String url) throws Exception {
        Exchange exc = new Request.Builder().get(url).buildExchange();
        client.call(exc);
        return exc;
    }

    private static @NotNull String getUrl(String host, int port) {
        return "http://" + host + ":" + port + "/";
    }

    private void init() {
        if (interval <= 0)
            throw new IllegalStateException("'interval' must be > 0");
        log.info("Starting HealthMonitor with interval of {} seconds", interval);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                () -> new Thread(healthCheckTask, "HealthCheckThread").start(),
                5, interval, TimeUnit.SECONDS
        );
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
        if (scheduler != null)
            scheduler.shutdown();
    }

    /**
     * @param interval the interval between health checks, in seconds
     * @description Sets the health check interval (in seconds).
     * @example <lbClusterHeathMonitor interval="30"/>
     * @default 10
     */
    @Required
    @MCAttribute
    public void setInterval(int interval) {
        this.interval = interval;
    }

    public Integer getInterval() {
        return interval;
    }
}
