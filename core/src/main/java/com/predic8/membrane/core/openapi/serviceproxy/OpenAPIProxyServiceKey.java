/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.rules.*;
import org.slf4j.*;

import java.util.*;

public class OpenAPIProxyServiceKey extends ServiceProxyKey {

    private static Logger log = LoggerFactory.getLogger(OpenAPIProxyServiceKey.class.getName());

    ArrayList<String> basePaths = new ArrayList<>();

    public OpenAPIProxyServiceKey(String ip, String host, int port) {
        super(host, "*", null, port, ip);

        // Add basePaths of OpenAPIPublisherInterceptor to accept them also
        basePaths.add(OpenAPIPublisherInterceptor.PATH);
        basePaths.add(OpenAPIPublisherInterceptor.PATH_UI);
    }

    @Override
    public boolean isMethodWildcard() {
        return true;
    }

    @Override
    public boolean complexMatch(String hostHeader, String method, String uri, String version, int port, String localIP) {
        for (String basePath : basePaths) {
            if (!uri.startsWith(basePath))
                continue;

            log.debug("Rule matches " + uri);
            return true;

        }
        return false;
    }

    @Override
    public String getPath() {
        return "*";
    }

    void addBasePaths(ArrayList<String> paths) {
        basePaths.addAll(paths);
    }
}
