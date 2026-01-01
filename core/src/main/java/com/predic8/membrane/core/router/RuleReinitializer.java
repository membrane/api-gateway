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

package com.predic8.membrane.core.router;

import com.predic8.membrane.core.proxies.*;
import org.slf4j.*;

import java.util.*;

public class RuleReinitializer {

    private static final Logger log = LoggerFactory.getLogger(RuleReinitializer.class);

    private final Router router;

    private Timer timer;

    public RuleReinitializer(Router router) {
        this.router = router;
    }

    synchronized void start() {
        log.info("Starting rule reinitializer.");
        if (timer != null)
            return; // Already started.

        if (getInactiveRules().isEmpty())
            return;

        timer = new Timer("reinitializer", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                retry();
            }
        }, router.getConfiguration().getRetryInitInterval(), router.getConfiguration().getRetryInitInterval());
    }

    public synchronized void stop() {
        if (timer != null) {
            timer.cancel();
        }
        timer = null;
    }

    /**
     * Should only be called from timer in this class
     */
    public void retry() {
        boolean stillFailing = false;
        List<Proxy> inactive = getInactiveRules();
        if (!inactive.isEmpty()) {
            log.info("Trying to activate all inactive rules.");
            for (Proxy proxy : inactive) {
                try {
                    log.info("Trying to start API {}.", proxy.getName());
                    Proxy newProxy = proxy.clone();
                    if (!newProxy.isActive()) {
                        log.warn("New rule for API {} is still not active.", proxy.getName());
                        stillFailing = true;
                    }
                    router.getRuleManager().replaceRule(proxy, newProxy);
                } catch (CloneNotSupportedException e) {
                    log.error("", e);
                }
            }
        }
        if (stillFailing)
            log.info("There are still inactive rules.");
        else {
            stop();
            log.info("All rules have been initialized.");
        }
    }


    List<Proxy> getInactiveRules() {
        ArrayList<Proxy> inactive = new ArrayList<>();
        for (Proxy proxy : router.getRuleManager().getRules())
            if (!proxy.isActive())
                inactive.add(proxy);
        return inactive;
    }
}
