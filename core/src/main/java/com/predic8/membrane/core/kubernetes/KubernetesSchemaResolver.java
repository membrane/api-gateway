/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.kubernetes;

import com.google.common.collect.Lists;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.kubernetes.client.KubernetesClient;
import com.predic8.membrane.core.kubernetes.client.KubernetesClientBuilder;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.resolver.SchemaResolver;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KubernetesSchemaResolver implements SchemaResolver {

    KubernetesClient kc;

    synchronized KubernetesClient getClient() {
        if (kc == null)
            try {
                kc = KubernetesClientBuilder.auto().build();
            } catch (KubernetesClientBuilder.ParsingException e) {
                throw new RuntimeException(e);
            }
        return kc;
    }

    @Override
    public List<String> getSchemas() {
        return Lists.newArrayList("kubernetes");
    }

    public InputStream resolve(String url) throws ResourceRetrievalException {
        try {
            if (!url.startsWith("kubernetes:"))
                throw new IllegalArgumentException();
            url = url.substring(11);
            if (url.startsWith("secret:")) {
                url = url.substring(7);
                int p = url.indexOf('/');
                String namespace = url.substring(0, p);
                url = url.substring(p+1);
                p = url.indexOf('/');
                String name = url.substring(0, p);
                String key = url.substring(p+1);

                Map secret = getClient().read("v1", "Secret", namespace, name);
                String res = (String) ((Map)secret.get("data")).get(key);
                return new ByteArrayInputStream(Base64.getDecoder().decode(res));
            }
            throw new ResourceRetrievalException(url);
        } catch (ResourceRetrievalException e) {
            throw e;
        } catch (Exception e) {
            ResourceRetrievalException rre = new ResourceRetrievalException(url, e);
            throw rre;
        }
    }

    @Override
    public void observeChange(String url, Consumer<InputStream> consumer) throws ResourceRetrievalException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public List<String> getChildren(String url) {
        return null;
    }

    @Override
    public long getTimestamp(String url) {
        return 0;
    }

}
