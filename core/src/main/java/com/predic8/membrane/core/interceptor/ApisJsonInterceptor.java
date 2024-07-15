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
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.lang.String.valueOf;
import static java.text.DateFormat.getDateTimeInstance;

@MCElement(name = "APIsJSON")
public class ApisJsonInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApisJsonInterceptor.class);

    private static final ObjectMapper om = new ObjectMapper();
    private static final String YYYY_MM_DD = "yyyy-MM-dd";
    private static final String SPECIFICATION_VERSION = "0.18";
    private byte[] apisJson;

    private String rootDomain;
    private String collectionId;
    private String collectionName;
    private String description;
    private String apisJsonUrl;
    private Date created;
    private Date modified;

    @Override
    public void init(Router router) throws JsonProcessingException {
        synchronized(this) {
            if (apisJson == null) {
                ObjectNode apisJsonNode = om.createObjectNode();
                apisJsonNode.put("aid", rootDomain + ":" + collectionId);
                apisJsonNode.put("name", collectionName);
                apisJsonNode.put("description", description);
                apisJsonNode.put("url", apisJsonUrl);
                apisJsonNode.put("created", new SimpleDateFormat(YYYY_MM_DD).format(created));
                apisJsonNode.put("modified", new SimpleDateFormat(YYYY_MM_DD).format(modified));
                apisJsonNode.put("specificationVersion", SPECIFICATION_VERSION);
                apisJsonNode.putArray("apis").addAll(
                        router.getRuleManager().getRules().stream()
                                .filter(APIProxy.class::isInstance)
                                .map(r -> jsonNodeFromApiProxy((APIProxy) r)).toList()
                );
                apisJson = om.writeValueAsBytes(apisJsonNode);
            }
        }
    }

    JsonNode jsonNodeFromApiProxy(APIProxy api) {
        ObjectNode apiJson = om.createObjectNode();
        apiJson.put("aid", rootDomain + ":" + safePathAsString(api.getPath()));
        apiJson.put("name", api.getName());
        apiJson.put("description", "N/A (Available only internally in APIProxy)");
        apiJson.put("humanUrl", "WIP");
        apiJson.put("baseUrl", safePathAsString(api.getPath()));
        apiJson.put("image", "N/A (Available only internally in APIProxy)");
        apiJson.put("version", "N/A (Available only internally in APIProxy)");
        return apiJson;
    }

    String safePathAsString(Path path) {
        return (path != null) ? path.getValue() : "/";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setResponse(new ResponseBuilder().body(apisJson).contentType(APPLICATION_JSON).build());
        return RETURN;
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