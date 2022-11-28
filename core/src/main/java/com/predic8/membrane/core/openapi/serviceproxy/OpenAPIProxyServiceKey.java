package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.rules.*;
import org.slf4j.*;

import java.util.*;

public class OpenAPIProxyServiceKey extends ServiceProxyKey {

    private static Logger log = LoggerFactory.getLogger(OpenAPIProxyServiceKey.class.getName());

    ArrayList<String> basePaths;

    public OpenAPIProxyServiceKey(int port) {
        super(port);
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

    void setBasePaths(ArrayList<String> paths) {
        basePaths=paths;
    }
}
