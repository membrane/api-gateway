/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

import com.predic8.membrane.annot.beanregistry.BeanDefinition;
import com.predic8.membrane.core.router.Router;

import java.io.File;

public final class BeanDefinitionBasePathUtil {

    private BeanDefinitionBasePathUtil() {}

    public static String resolveBaseLocation(Object bean, Router router) {
        if (bean instanceof BeanDefinition beanDefinition) {
            return resolveBaseLocation(beanDefinition, router);
        }
        if (router != null && router.getRegistry() != null) {
            return resolveBaseLocation(router.getRegistry().getBeanDefinition(bean), router);
        }
        return null;
    }

    public static String resolveBaseLocation(BeanDefinition beanDefinition, Router router) {
        if (beanDefinition != null) {
            var sourceMetadata = beanDefinition.getSourceMetadata();
            if (sourceMetadata != null && sourceMetadata.sourceFile() != null) {
                var sourceDirectory = sourceMetadata.sourceFile().getParent();
                if (sourceDirectory != null) {
                    return ensureDirectorySemantics(sourceDirectory.toAbsolutePath().normalize().toString());
                }
            }
        }
        if (router == null) return null;
        return router.getConfiguration().getBaseLocation();
    }

    private static String ensureDirectorySemantics(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        if (path.endsWith("/") || path.endsWith("\\")) {
            return path;
        }
        return path + File.separator;
    }
}
