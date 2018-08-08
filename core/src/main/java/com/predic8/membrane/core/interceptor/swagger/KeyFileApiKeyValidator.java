package com.predic8.membrane.core.interceptor.swagger;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@MCElement(name="keyFile")
public class KeyFileApiKeyValidator implements ApiKeyValidator {

    private String location;

    public String getLocation() {
        return location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    Map<String, Boolean> validKeys = new ConcurrentHashMap<String, Boolean>();

    public void init(Router router) throws Exception {
        InputStream is = router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), location));
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        while(true) {
            String line = br.readLine();
            if (line == null)
                break;

            String key = line.split(" ", 2)[0];
            validKeys.put(key, Boolean.TRUE);
        }
    }

    @Override
    public boolean isValid(String key) {
        return validKeys.containsKey(key);
    }
}
