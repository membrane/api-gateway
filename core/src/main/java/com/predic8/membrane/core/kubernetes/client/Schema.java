/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.kubernetes.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class Schema {
    private static final Logger LOG = LoggerFactory.getLogger(Schema.class);
    @SuppressWarnings({"rawtypes"})
    private final Map s20;
    @SuppressWarnings({"rawtypes"})
    private final Map apis;

    @SuppressWarnings({"rawtypes"})
    public Schema(Map s20, Map apis) {
        this.s20 = s20;
        this.apis = apis;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public static Schema getSchema(KubernetesClient client, ObjectMapper om) throws IOException, HttpException {
        Map s20 = null;
        HttpException exc = null;
        try {
            s20 = om.readValue(get(client, "/openapi/v2"), Map.class);
        } catch (HttpException e) {
            exc = e;
        }

        if (s20 == null) {
            try {
                s20 = om.readValue(get(client, "/swagger-2.0.0.json"), Map.class);
            } catch (HttpException e) {
                if (exc == null)
                    exc = e;
            }
        }

        if (exc != null)
            throw exc;

        Map apis = om.readValue(get(client, "/apis"), Map.class);

        Map r = new HashMap();
        ((List)apis.get("groups")).forEach(g -> {
            Map group = (Map)g;
            Map groupInfo = new HashMap<>();
            r.put(group.get("name"), groupInfo);
            ((List)group.get("versions")).forEach(v -> {
                Map version = (Map)v;
                String theVersion = (String) version.get("version");
                try {
                    Map versionInfo = om.readValue(getWithRetries(client,
                            "/apis/" + group.get("name") + "/" + theVersion), Map.class);
                    groupInfo.put(theVersion, versionInfo);
                } catch (Exception e) {
                    LOG.warn("Could not retrieve /apis/" + group.get("name") + "/" + theVersion + ": " + e.getMessage());
                }
            });
        });

        return new Schema(s20, r);
    }

    static InputStream get(KubernetesClient client, String path) throws IOException, HttpException {
        Exchange e = null;
        try {
            e = new Request.Builder().get(client.getBaseURL() + path).buildExchange();
            client.getClient().call(e);
        } catch (Exception ex) {
            throw new IOException(ex);
        }

        if (e.getResponse().getStatusCode() != 200) {
            e.getResponse().getBodyAsStringDecoded();

            throw new HttpException(e.getResponse().getStatusCode(), "retrieving " + path + " caused " +
                    e.getResponse().getStatusMessage());
        }

        return e.getResponse().getBodyAsStreamDecoded();
    }

    static InputStream getWithRetries(KubernetesClient client, String path) throws IOException, HttpException {
        Exception ex = null;
        for (int i = 0; i < 30; i++) {
            try {
                return get(client, path);
            } catch (IllegalStateException e) {
                throw e;
            } catch (IOException | HttpException e) {
                ex = e;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ex instanceof HttpException)
            throw (HttpException)ex;
        throw (IOException)ex;
    }


    @SuppressWarnings({"unchecked","rawtypes"})
    public String getPath(String verb, String apiVersion, String kind, boolean forAllNamespaces) {
        AtomicReference<String> thepath = new AtomicReference<>();
        ((Map)s20.get("paths")).forEach( (path, methods) -> ((Map)methods).forEach((method, data) -> {
            try {
                if (data instanceof ArrayList)
                    return;
                String apiv = apiVersion;
                if (!apiVersion.contains("/"))
                    apiv = "core/" + apiv;
                apiv = apiv.replaceAll("/", "")
                        .replaceAll("\\.k8s\\.io", "")
                        .replaceAll("[.-]", "");

                String operationId = (String) ((Map) data).get("operationId");
                if (forAllNamespaces && operationId.equalsIgnoreCase(verb + apiv + kind + "ForAllNamespaces")) {
                    thepath.set((String)path);
                }

                if (operationId.equalsIgnoreCase(verb + apiv + kind) && thepath.get() == null) {
                    thepath.set((String)path);
                }
                if (!forAllNamespaces && operationId.equalsIgnoreCase(verb + apiv + "Namespaced" + kind)) {
                    thepath.set((String)path);
                }
            } catch (Exception e) {
                throw new RuntimeException(path + " " + method, e);
            }
        }));
        if (thepath.get() == null) {
            String[] av = apiVersion.split("/", 2);
            String verb2 = verb.equals("read") ? "get" : verb;
            if (av.length == 2) {
                if (apis != null && apis.get(av[0]) != null) {
                    Map api = (Map) ((Map) apis.get(av[0])).get(av[1]);
                    if (api != null) {
                        ((List) api.get("resources")).forEach(res -> {
                            if (((Map)res).get("kind").equals(kind) && ((List)((Map)res).get("verbs")).contains(verb2)) {
                                thepath.set("/apis/"+api.get("groupVersion")+"/namespaces/{namespace}/" +
                                        ((Map)res).get("name") +
                                        (verb2.equals("list") || verb2.equals("create") ? "" : "/{name}"));
                            }
                        });
                    }
                }
            }
        }

        if (thepath.get() == null)
            throw new RuntimeException("Could not determine path for " + apiVersion + "/" + kind + " .");

        return thepath.get();
    }

}
