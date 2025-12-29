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

package com.predic8.membrane.core;

import com.predic8.membrane.core.proxies.*;
import org.slf4j.*;

import java.util.*;

public class RuleReinitializer {

    private static final Logger log = LoggerFactory.getLogger(RuleReinitializer.class);

    private Router router;

    private Timer timer;

    public RuleReinitializer(Router router) {
        this.router = router;
    }

    void startAutoReinitializer() {
        if (getInactiveRules().isEmpty())
            return;

        timer = new Timer("auto reinitializer", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                tryReinitialization();
            }
        }, router.getConfig().getRetryInitInterval(), router.getConfig().getRetryInitInterval());
    }

    public void tryReinitialization() {
        boolean stillFailing = false;
        ArrayList<Proxy> inactive = getInactiveRules();
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
            stopAutoReinitializer();
            log.info("All rules have been initialized.");
        }
    }

    public void stopAutoReinitializer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    ArrayList<Proxy> getInactiveRules() {
        ArrayList<Proxy> inactive = new ArrayList<>();
        for (Proxy proxy : router.getRuleManager().getRules())
            if (!proxy.isActive())
                inactive.add(proxy);
        return inactive;
    }
}
