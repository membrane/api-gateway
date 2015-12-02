package com.predic8.membrane.core.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExponentialBackoff {
    private static final Logger LOG = LoggerFactory.getLogger(ExponentialBackoff.class);

    public interface Job {
        boolean run() throws Exception;
    }

    public static void retryAfter(long initialDelay, long maxDelay, double factor, String jobDescription, Job job) throws InterruptedException {
        long delay = initialDelay;

        while (true) {
            Exception f = null;
            try {
                if (job.run())
                    break;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                f = e;
            }
            if (f != null)
                LOG.error(jobDescription+" failed. Retrying in " + (delay / 1000) + "s.", f);
            else
                LOG.info(jobDescription+" failed. Retrying in " + (delay / 1000) + "s.");
            Thread.sleep(delay);
            delay = Math.min(maxDelay, (long)(delay * factor));
        }
        LOG.debug(jobDescription + " succeeded.");
    }
}
