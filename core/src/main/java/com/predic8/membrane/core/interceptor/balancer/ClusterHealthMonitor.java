package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.balancer.Node.*;
import com.predic8.membrane.core.transport.http.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.context.*;

import java.util.concurrent.*;

import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.*;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.*;
import static java.lang.System.currentTimeMillis;

/**
 * @description Configuration element for scheduling periodic cluster health checks.
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name = "lbClusterHeathMonitor")
public class ClusterHealthMonitor implements ApplicationContextAware, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ClusterHealthMonitor.class);

    private Router router;
    private int interval = 10;
    private ScheduledExecutorService scheduler;
    private static final HttpClient client = new HttpClient();

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

    private final Runnable healthCheckTask = () -> {
        log.info("Starting Load Balancer Health Check");
        collectClusters(router).forEach(cluster -> {
            log.info("Checking cluster '{}'", cluster.getName());
            cluster.getNodes().forEach(node -> node.setStatus(isHealthy(node)));
        });
        log.info("Health Check complete");
    };

    private Status isHealthy(Node node) {
        String url = getNodeHealthEndpoint(node);
        try {
            return getStatus(node,doCall(url)) ;
        } catch (Exception e) {
            log.error("Error Calling: {}, {}", url, e.getMessage());
            return DOWN;
        }
    }

    private static Status getStatus(Node node, Exchange exc) {
        try {
            int status = exc.getResponse().getStatusCode();
            if (status >= 300) {
                log.error("Node {}:{} health check failed with HTTP {}", node.getHost(), node.getPort(), status);
                return DOWN;
            }
            log.info("Node {}:{} is healthy (HTTP {})", node.getHost(), node.getPort(), status);
            if (node.isDown())
                node.setLastUpTime(currentTimeMillis());
            return UP;

        } catch (Exception e) {
            log.error("Unexpected error during health check for node {}:{} - marking DOWN", node.getHost(), node.getPort(), e);
            return DOWN;
        }
    }

    private static String getNodeHealthEndpoint(Node node) {
        return node.getHealthUrl() != null
                ? node.getHealthUrl()
                : getUrl(node.getHost(), node.getPort());
    }

    private Exchange doCall(String url) throws Exception {
        Exchange exc = new Request.Builder().get(url).buildExchange();
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
