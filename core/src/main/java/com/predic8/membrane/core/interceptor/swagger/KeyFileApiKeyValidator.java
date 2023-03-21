/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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

    /**
     * @description A file/resource containing one API key description per line. Lines starting with '#' are ignored. An API key description consists of the API key (string without spaces) followed by a space and possibly arbitrary characters (except newlines), for example a description.
     * See <a href="https://www.membrane-soa.org/service-proxy-doc/current/configuration/location.htm">here</a> for a description of the format.
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    Map<String, Boolean> validKeys = new ConcurrentHashMap<>();

    public void init(Router router) throws Exception {
        InputStream is = router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), location));
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        while(true) {
            String line = br.readLine();
            if (line == null)
                break;

            if (line.startsWith("#"))
                continue;

            String key = line.split(" ", 2)[0];
            validKeys.put(key, Boolean.TRUE);
        }
    }

    @Override
    public boolean isValid(String key) {
        return validKeys.containsKey(key);
    }
}
