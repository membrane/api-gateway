/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Response.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import com.predic8.membrane.core.proxies.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.text.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.Optional.*;

@MCElement(name = "APIsJSON")
public class ApisJsonInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApisJsonInterceptor.class);

    private static final ObjectMapper om = new ObjectMapper();
    private static final String YYYY_MM_DD = "yyyy-MM-dd";
    private static final String SPECIFICATION_VERSION = "0.18";
    private byte[] apisJson;

    private String rootDomain = "membrane";
    private String collectionId = "apis";
    private String collectionName = "APIs";
    private String description = "APIs.json Document";
    private String apisJsonUrl;
    private Date created = new Date();
    private Date modified = new Date();

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (apisJson == null) {
            try {
                initJson(router, exc);
            } catch (JsonProcessingException e) {
                internal(router.isProduction(),getDisplayName())
                        .detail("Could not create APIs JSON!")
                        .exception(e)
                        .buildAndSetResponse(exc);
                return ABORT;
            }
        }
        exc.setResponse(new ResponseBuilder().body(apisJson).contentType(APPLICATION_JSON).build());
        return RETURN;
    }

    public void initJson(Router router, Exchange exc) throws JsonProcessingException {
        if (apisJson != null) {
            return;
        }
        if (apisJsonUrl == null) {
            apisJsonUrl = getProtocol(exc.getProxy()) + exc.getRequest().getHeader().getHost() + exc.getRequest().getUri();
        }
        ObjectNode apis = om.createObjectNode();
        apis.put("aid", rootDomain + ":" + collectionId);
        apis.put("name", collectionName);
        apis.put("description", description);
        apis.put("url", apisJsonUrl);
        apis.put("created", new SimpleDateFormat(YYYY_MM_DD).format(created));
        apis.put("modified", new SimpleDateFormat(YYYY_MM_DD).format(modified));
        apis.put("specificationVersion", SPECIFICATION_VERSION);
        apis.putArray("apis").addAll(
                (router.getRuleManager().getRules().stream()
                        .filter(APIProxy.class::isInstance)
                        .<JsonNode>mapMulti((rule, sink) -> {
                            var r = ((APIProxy) rule);
                            if (r.getApiRecords().isEmpty())
                                sink.accept(jsonNodeFromApiProxy(r, null, null));
                            else
                                r.getApiRecords().forEach((id, rec) -> sink.accept(jsonNodeFromApiProxy(r, id, rec)));
                        }).toList())
        );
        apisJson = om.writeValueAsBytes(apis);
    }

    JsonNode jsonNodeFromApiProxy(APIProxy api, String recordId, OpenAPIRecord apiRecord) {
        ObjectNode apiJson = om.createObjectNode();
        apiJson.put("aid", customIdOrBuildDefault(api, recordId));
        apiJson.put("name", (apiRecord != null) ? apiRecord.getApi().getInfo().getTitle() : api.getName());
        apiJson.put("description", (apiRecord != null && api.getDescription() == null
                                    && apiRecord.getApi().getInfo().getDescription() != null)
                ? apiRecord.getApi().getInfo().getDescription()
                : ofNullable(api.getDescription()).map(ApiDescription::getContent).orElse("API"));
        apiJson.put("humanUrl", getProtocol(api) + getHost(api) + ((apiRecord != null) ? "/api-docs/ui/" + recordId : "/api-docs"));
        apiJson.put("baseUrl", getProtocol(api) + getHost(api) + ofNullable(api.getPath()).map(Path::getValue).orElse("/"));
        if (apiRecord != null)
            apiJson.put("version", apiRecord.getApi().getInfo().getVersion());
        return apiJson;
    }

    private static String getHost(APIProxy api) {
        String hostname = (Objects.equals(api.getKey().getHost(), "*")) ? "localhost" : api.getKey().getHost();

        return hostname + ((api.getPort() == 80 || api.getPort() == 443) ? "" : ":" + api.getPort());
    }

    private String customIdOrBuildDefault(APIProxy api, String recordId) {
        if (api.getId() != null)
            return api.getId();
        return (recordId != null) ? rootDomain + ":" + recordId : buildDefaultAPIProxyId(api);
    }

    private String buildDefaultAPIProxyId(APIProxy api) {
        return rootDomain + ":" + ((APIProxyKey) api.getKey()).getKeyId();
    }

    private static @NotNull String getProtocol(Proxy proxy) {
        return proxy.getProtocol() + "://";
    }

    @MCAttribute
    public void setRootDomain(String rootDomain) {
        this.rootDomain = rootDomain;
    }

    @MCAttribute
    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    @MCAttribute(attributeName = "name")
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    @MCAttribute
    public void setDescription(String description) {
        this.description = description;
    }

    @MCAttribute(attributeName = "url")
    public void setApisJsonUrl(String apisJsonUrl) {
        this.apisJsonUrl = apisJsonUrl;
    }

    @MCAttribute
    public void setCreated(String created) throws ParseException {
        this.created = new SimpleDateFormat(YYYY_MM_DD).parse(created);
    }

    @MCAttribute
    public void setModified(String modified) throws ParseException {
        this.modified = new SimpleDateFormat(YYYY_MM_DD).parse(modified);
    }

    @Override
    public String getDisplayName() {
        return "APIs Json";
    }

    @Override
    public String getShortDescription() {
        return "Displays all deployed API Proxies in APIs.json format";
    }

    @Override
    public String getLongDescription() {
        return "WIP"; // TODO Add long description
    }
}