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
package com.predic8.membrane.core.proxies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.util.text.TerminalColors.BRIGHT_GREEN;
import static com.predic8.membrane.core.util.text.TerminalColors.RESET;

public class ApiInfo {

    private static final Logger log = LoggerFactory.getLogger(ApiInfo.class.getName());

    public static void logInfosAboutStartedProxies(RuleManager manager) {
        if (manager.getRules().isEmpty()) {
            // Nothing started yet. Happens when the router is started with on K8S startup.
            return;
        }
        log.info("Started {} API{}:", manager.getRules().size(), (manager.getRules().size() > 1 ? "s" : ""));
        manager.getRules().forEach(proxy ->
                log.info(" {}{} {}{}{}", BRIGHT_GREEN(), proxy.getKindLabel(), proxy.getStartupLabel(), RESET(), proxy.getStartupDetails())
        );
    }
}
