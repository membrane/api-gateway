package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.collectClusters;

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

    private final Runnable healthCheckTask = () -> {
        log.info("Starting Health Check");
        List<Cluster> clusters = collectClusters(router);
        clusters.forEach(cluster -> {
            log.info("Checking cluster '{}'", cluster.getName());
            cluster.getNodes().forEach(this::processNode);
        });
        log.info("Health Check complete");
    };

    private void processNode(Node node) {
        String host = node.getHost();
        int port = node.getPort();
        String url = "http://" + host + ":" + port + "/";
        int status = -1;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            status = conn.getResponseCode();
            if (status >= 500) {
                log.error("Node {}:{} returned HTTP {}", host, port, status);
            } else {
                log.info("Node {}:{} returned HTTP {}", host, port, status);
            }
        } catch (IOException e) {
            log.error("Failed to call node {}:{} - {}", host, port, e.toString());
        }
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
     * @description Sets the health check interval (in seconds).
     * @param interval the interval between health checks, in seconds
     * @example <lbClusterHeathMonitor interval="30"/>
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
