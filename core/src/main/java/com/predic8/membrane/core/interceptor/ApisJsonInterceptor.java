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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy.ApiDescription;
import com.predic8.membrane.core.rules.Rule;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.lang.String.valueOf;
import static java.text.DateFormat.getDateTimeInstance;
import static java.util.Optional.ofNullable;

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
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (apisJson == null)
            initJson(router, exc);
        exc.setResponse(new ResponseBuilder().body(apisJson).contentType(APPLICATION_JSON).build());
        return RETURN;
    }

    public void initJson(Router router, Exchange exc) throws JsonProcessingException {
        if (apisJson != null) {
            return;
        }
        if (apisJsonUrl == null) {
            apisJsonUrl = getProtocol(exc.getRule()) + exc.getRequest().getHeader().getHost() + exc.getRequest().getUri();
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
        apiJson.put("description", (apiRecord != null && apiRecord.getApi().getInfo().getDescription() != null)
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

    private static @NotNull String getProtocol(Rule rule) {
        return (rule.getSslInboundContext() != null ? "https" : "http") + "://";
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