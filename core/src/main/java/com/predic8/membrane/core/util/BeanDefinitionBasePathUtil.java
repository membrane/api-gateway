package com.predic8.membrane.core.util;

import com.predic8.membrane.annot.beanregistry.BeanDefinitionAware;
import com.predic8.membrane.core.router.Router;

public final class BeanDefinitionBasePathUtil {

    private BeanDefinitionBasePathUtil() {}

    public static String resolveBaseLocation(BeanDefinitionAware bean, Router router) {
        if (bean != null && bean.getBeanDefinition() != null) {
            var sourceMetadata = bean.getBeanDefinition().getSourceMetadata();
            if (sourceMetadata != null && sourceMetadata.basePath() != null) {
                return sourceMetadata.basePath().toString();
            }
        }
        if (router == null) return null;
        return router.getConfiguration().getBaseLocation();
    }
}
