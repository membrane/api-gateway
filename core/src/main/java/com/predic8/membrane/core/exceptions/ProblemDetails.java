/* Copyright 2023 predic8 GmbH, www.predic8.com
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
   http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exceptions;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;


/**
 * Utility class for <a href="https://datatracker.ietf.org/doc/html/rfc7807">ProblemDetails Spec</a>
 */
public class ProblemDetails {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetails.class.getName());
  
    private static final ObjectMapper om = new ObjectMapper();
    private final static ObjectWriter ow = om.writerWithDefaultPrettyPrinter();

    private boolean production;

    private int statusCode;
    private String type;

    private String title;
    private String detail;

    /**
     * Component like plugin
     */
    private String component;
    
    private String instance;
    private final HashMap<String, Object> extensions = new LinkedHashMap<>();
    private Throwable exception;

    /**
     * Include stacktrace. If the stacktrace does not provide any details set this value to false
     */
    private boolean stacktrace = true;

    public static ProblemDetails user(boolean production) {
        return problemDetails("user", production).statusCode(400);
    }

    public static ProblemDetails internal(boolean production) {
        return problemDetails("internal", production).statusCode(500).title("Internal server error.");
    }

    public static ProblemDetails gateway(boolean production) {
        return problemDetails( "gateway", production).statusCode(500).title("Gateway error.");
    }

    public static ProblemDetails security(boolean production) {
        return problemDetails( "security", production);
    }

    public static ProblemDetails openapi(boolean production) {
        return problemDetails("openapi", production);
    }

    public static ProblemDetails problemDetails(String type, boolean production) {
        ProblemDetails pd = new ProblemDetails();
        pd.type = type;
        pd.production = production;
        return pd;
    }

    public ProblemDetails addSubType(String subType) {
        this.type += "/" + subType;
        return this;
    }

    public ProblemDetails statusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public ProblemDetails title(String title) {
        this.title = title;
        return this;
    }

    /**
     * @param humanReadableExplanation A human-readable explanation specific to this
     *                                 occurrence of the problem.
     * @return Instance
     */
    public ProblemDetails detail(String humanReadableExplanation) {
        if (humanReadableExplanation != null)
            this.detail = humanReadableExplanation;
        return this;
    }
    
    public ProblemDetails component(String component) {
        this.component = component;
        return this;
    }

    public ProblemDetails instance(String instance) {
        this.instance = instance;
        return this;
    }

    public ProblemDetails extension(String key, Object value) {
        this.extensions.put(key, value);
        return this;
    }

    public ProblemDetails exception(Throwable e) {
        this.exception = e;
        return this;
    }

    public ProblemDetails stacktrace(boolean stacktrace) {
        this.stacktrace = stacktrace;
        return this;
    }

    public Response build() {
        return createContent(createMap(), null);
    }

    /**
     * Does only log, when key in log is needed. The caller is responsible to do the log if
     * there is something interessting.
     */
    public void buildAndSetResponse(Exchange exchange) {
        if (exchange != null) {
            exchange.setResponse(createContent(createMap(), exchange));
            return;
        }
        throw new RuntimeException("Should not happen!");
    }

    private @NotNull Map<String, Object> createMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> extensionsMap = new LinkedHashMap<>();

        if (production) {
            String logKey = UUID.randomUUID().toString();
            log.warn("logKey={}\ntype={}\ntitle={}\n,detail={}\n,extension={},.", logKey, type, title, detail, extensionsMap);

            type = "internal";
            title = "An error occurred.";
            detail = "Details can be found in the Membrane log searching for key: %s.".formatted(logKey);
        } else {
            extensionsMap.putAll(extensions);
            if (exception != null) {
                extensionsMap.put("message",exception.getMessage());
                if (stacktrace) {
                    extensionsMap.put("stackTrace", getStackTrace());
                }
            }
            extensionsMap.put("attention", """
                Membrane is in development mode. For production set <router production="true"> to reduce details in error messages!""");
        }

        root.put("title", title);
        root.put("type", "https://membrane-api.io/error/" + type);

        if (!production && component != null) {
            root.put("component", component);
        }

        if (detail != null) {
            root.put("detail", detail);
        }

        root.putAll(extensionsMap);
        return root;
    }

    private @NotNull Map getStackTrace() {
        var m = new LinkedHashMap<>();
            for (int i = 0; i < exception.getStackTrace().length; i++) {
                m.put("e"+i , exception.getStackTrace()[i].toString());
            }
        return m;
    }

    private Response createContent(Map<String, Object> root, Exchange exchange) {
        Response.ResponseBuilder builder = Response.statusCode(statusCode);
        try {
            if (exchange != null && exchange.getRequest().isXML())
                createXMLContent(root, builder);
            else
                createJson(root, builder);
        } catch (Exception e) {
            builder.body("Title: %s\nType: %s\n%s".formatted(type, title, root).getBytes());
            builder.contentType(TEXT_PLAIN);
        }
        return builder.build();
    }

    private static void createXMLContent(Map<String, Object> root, Response.ResponseBuilder builder) throws Exception {
        builder.body(convertMapToXml(root));
        builder.contentType(APPLICATION_XML);
    }

    public static String convertMapToXml(Map<String, Object> map) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = document.createElement("problem-details");
        document.appendChild(root);
        mapToXmlElements(map, document, root);
        return document2string(document);
    }

    private static String document2string(Document document) throws TransformerException {
        StringWriter writer = new StringWriter();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private static void mapToXmlElements(Map<String, Object> map, Document document, Element parent) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null)
                continue;
            Element element = document.createElement(entry.getKey());
            if (value instanceof Map mv) {
                mapToXmlElements(mv, document, element);
            } else if (value instanceof Object[] oa) {
                for (Object obj : oa) {
                    Element arrayElement = document.createElement(entry.getKey());
                    arrayElement.setTextContent(obj.toString());
                    parent.appendChild(arrayElement);
                }
                continue;
            } else {
                element.setTextContent(value.toString());
            }
            parent.appendChild(element);
        }
    }

    private static void createJson(Map<String, Object> root, Response.ResponseBuilder builder) throws JsonProcessingException {
        builder.body(ow.writeValueAsBytes(root));
        builder.contentType(APPLICATION_PROBLEM_JSON);
    }

    public static ProblemDetails parse(Response r) throws JsonProcessingException {

        if (r.getHeader().getContentType() == null)
            throw  new RuntimeException("No Content-Type in message with ProblemDetails!");

        if (!r.getHeader().getContentType().equals(APPLICATION_PROBLEM_JSON))
            throw new RuntimeException("Content-Type ist %s but should be %s.".formatted(r.getHeader().getContentType(),APPLICATION_PROBLEM_JSON));

        ProblemDetails pd = new ProblemDetails();
        pd.statusCode(r.getStatusCode());

        TypeReference<Map<String,Object>> typeRef = new TypeReference<>() {};

        Map<String, Object> m = om.readValue(r.getBodyAsStringDecoded(), typeRef);

        pd.type = (String) m.get("type");
        pd.title = (String) m.get("title");
        pd.detail = (String) m.get("detail");
        pd.instance = (String) m.get("instance");

        for (Map.Entry<String, Object> e :m.entrySet()) {
            if(pd.isReservedProblemDetailsField(e.getKey()))
                continue;
            pd.extension(e.getKey(),e.getValue());
        }
        return pd;
    }

    private boolean isReservedProblemDetailsField(String key) {
        for (String reserved : List.of("type","title","detail","instance")) {
            if (key.equals(reserved))
                return true;
        }
        return false;
    }

    public boolean isProduction() {
        return production;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getInstance() {
        return instance;
    }

    public HashMap<String, Object> getExtensions() {
        return extensions;
    }

    public Throwable getException() {
        return exception;
    }

    @Override
    public String toString() {
        return "ProblemDetails{" +
               "production=" + production +
               ", statusCode=" + statusCode +
               ", type='" + type + '\'' +
               ", title='" + title + '\'' +
               ", detail='" + detail + '\'' +
               ", instance='" + instance + '\'' +
               ", extensions=" + extensions +
               ", exception=" + exception +
               '}';
    }
}