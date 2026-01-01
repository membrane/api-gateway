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

import com.predic8.membrane.core.config.spring.*;
import org.slf4j.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;

import java.nio.charset.*;
import java.util.*;

/**
 * Bootstrapping a {@link DefaultRouter} instance using Spring XML-based configuration.
 */
public class RouterXmlBootstrap {

    private static final Logger log = LoggerFactory.getLogger(RouterXmlBootstrap.class);

        /**
     * Initializes a {@link DefaultRouter} instance from the specified Spring XML configuration resource.
     *
     * @param resource the path to the Spring XML configuration file that defines the {@link DefaultRouter} bean
     * @return the initialized {@link DefaultRouter} instance
     * @throws RuntimeException if no {@link DefaultRouter} bean is found or more than one {@link DefaultRouter} bean is found
     */
    public static DefaultRouter initByXML(String resource) {
        log.debug("loading spring config: {}", resource);

        TrackingFileSystemXmlApplicationContext bf =
                new TrackingFileSystemXmlApplicationContext(new String[]{resource}, false);
        bf.refresh();

        if (bf.getBeansOfType(DefaultRouter.class).size() > 1) {
            throw new RuntimeException(
                    "More than one router bean found in the Spring configuration (%s). This is no longer supported."
                            .formatted(Arrays.toString(bf.getBeanDefinitionNames()))
            );
        }
        DefaultRouter router = bf.getBean("router", DefaultRouter.class);
        bf.start(); // Starting ApplicationContext will also call router.start(). Init should happen before.
        return router;
    }

    public static DefaultRouter initFromXMLString(String xmlString) {
        log.debug("Loading spring config from string");
        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
        ctx.load(new ByteArrayResource(xmlString.getBytes(StandardCharsets.UTF_8)));
        ctx.refresh();
        ctx.start();
        return ctx.getBean(DefaultRouter.class);
    }
}
