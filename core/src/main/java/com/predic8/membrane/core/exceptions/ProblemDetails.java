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
import com.predic8.membrane.core.interceptor.*;
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
import static com.predic8.membrane.core.util.ExceptionUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Locale.ROOT;
import static java.util.UUID.*;


/**
 * Utility class for <a href="https://datatracker.ietf.org/doc/html/rfc7807">ProblemDetails Spec</a>
 */
public class ProblemDetails {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetails.class.getName());

    private static final ObjectMapper om = new ObjectMapper();
    private static final ObjectWriter ow = om.writerWithDefaultPrettyPrinter();

    public static final String TITLE = "title";
    public static final String TYPE = "type";
    public static final String DETAIL = "detail";
    public static final String ATTENTION = "attention";
    public static final String SEE = "see";
    public static final String INSTANCE = "instance";


    private boolean logKeyInsteadOfDetails;

    private int statusCode;
    private String type;
    private String subType = "";

    private String title;
    private String detail;

    private Interceptor.Flow flow;
    private String seeSuffix = "";

    /**
     * Component e.g. plugin
     */
    private String component;

    /**
     * Internal information that is not returned with logKey
     */
    private final HashMap<String, Object> internalFields = new LinkedHashMap<>();

    /**
     * Toplevel elements that are always returned to the client
     */
    private final HashMap<String, Object> topLevel = new LinkedHashMap<>();
    private Throwable exception;

    /**
     * Include stacktrace. If the stacktrace does not provide any details set this value to false
     */
    private boolean stacktrace = true;

    public static ProblemDetails user(boolean logKeyInsteadOfDetails, String component) {
        return problemDetails("user", logKeyInsteadOfDetails)
                .statusCode(400)
                .title("User error.")
                .component(component);
    }

    public static ProblemDetails internal(boolean logKeyInsteadOfDetails, String component) {
        return problemDetails("internal", logKeyInsteadOfDetails)
                .statusCode(500)
                .title("Internal server error.")
                .component(component);
    }

    public static ProblemDetails gateway(boolean logKeyInsteadOfDetails, String component) {
        return problemDetails("gateway", logKeyInsteadOfDetails)
                .statusCode(500)
                .title("Gateway error.")
                .component(component);
    }

    public static ProblemDetails security(boolean logKeyInsteadOfDetails, String component) {
        return problemDetails("security", logKeyInsteadOfDetails)
                .statusCode(500)
                .title("Security error.")
                .component(component);
    }

    public static ProblemDetails openapi(boolean logKeyInsteadOfDetails, String component) {
        return problemDetails("openapi", logKeyInsteadOfDetails)
                .statusCode(400)
                .title("OpenAPI error.")
                .component(component);
    }

    public static ProblemDetails problemDetails(String type, boolean logKeyInsteadOfDetails) {
        ProblemDetails pd = new ProblemDetails();
        pd.type = type;
        pd.logKeyInsteadOfDetails = logKeyInsteadOfDetails;
        return pd;
    }

    /**
     * type/subtype/subtype/...
     * lowercase, dash as separator
     *
     * @param subType
     * @return
     */
    public ProblemDetails addSubType(String subType) {
        this.subType += "/" + subType;
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

    public ProblemDetails flow(Interceptor.Flow flow) {
        this.flow = flow;
        return this;
    }

    public ProblemDetails addSubSee(String s) {
        this.seeSuffix += s;
        return this;
    }

    public ProblemDetails internal(String key, Object value) {
        this.internalFields.put(key, value);
        return this;
    }

    public ProblemDetails topLevel(String key, Object value) {
        this.topLevel.put(key, value);
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
     * there is something interesting.
     */
    public void buildAndSetResponse(Exchange exchange) {
        exchange.setResponse(createContent(createMap(), exchange));
    }

    private @NotNull Map<String, Object> createMap() {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put(TITLE, title);
        root.put(TYPE, getTypeSubtypeString());
        root.putAll(topLevel);

        if (logKeyInsteadOfDetails) {
            provideLogKeyInsteadOfDetails(root);
            return root;
        }

        if (detail != null) {
            root.put(DETAIL, detail);
        }

        root.putAll(createInternal(getTypeSubtypeString()));
        return root;
    }

    private void provideLogKeyInsteadOfDetails(Map<String, Object> root) {
        String logKey = randomUUID().toString();
        log.warn("logKey={}\ntype={}\ntitle={}\n,detail={}\n,internal={},.", logKey, getTypeSubtypeString(), title, detail, internalFields);
        root.put(DETAIL, "Details can be found in the Membrane log searching for key: %s.".formatted(logKey));
        if (type.equals("internal")) {
            title = "Internal error";
        }
        if (stacktrace && exception != null) {
            log.warn("", exception);
        }
    }

    private @NotNull String getTypeSubtypeString() {
        String type = "https://membrane-api.io/problems/" + this.type;
        if (!subType.isEmpty()) {
            type += subType;
        }
        return type;
    }

    private String normalizeForType(String s) {
        return s.replace(" ", "-").toLowerCase(ROOT);
    }

    private Map<String, Object> createInternal(String type) {
        var internalMap = new LinkedHashMap<>(internalFields);
        if (exception != null) {
            if (internalMap.containsKey("message"))
                log.error("Overriding ProblemDetails extensionsMap 'message' entry. Please notify Membrane developers.", new RuntimeException());
            internalMap.put("message", concatMessageAndCauseMessages(exception));
            if (stacktrace) {
                internalMap.put("stackTrace", getStackTrace(exception, new StackTraceElement[0]));
            }
        }

        String see = type;
        if (!component.isEmpty()) {
            see += "/" + normalizeForType(component);
        }
        if (flow != null) {
            see += "/" + flow.name().toLowerCase(ROOT);
        }
        if (!seeSuffix.isEmpty()) {
            see += "/" + seeSuffix;
        }
        internalMap.put(SEE, see);

        internalMap.put(ATTENTION, """
                Membrane is in development mode. For production set <router production="true"> to reduce details in error messages!""");
        return internalMap;
    }

    private static @NotNull Map getStackTrace(Throwable exception, StackTraceElement[] enclosingTrace) {
        var m = new LinkedHashMap<>();

        StackTraceElement[] trace = exception.getStackTrace();
        int m2 = trace.length - 1;
        int n = enclosingTrace.length - 1;
        while (m2 >= 0 && n >=0 && trace[m2].equals(enclosingTrace[n])) {
            m2--; n--;
        }
        int framesInCommon = trace.length - 1 - m2;

        for (int i = 0; i <= m2; i++) {
            m.put("e" + i, trace[i].toString());
        }

        if (framesInCommon != 0) {
            m.put("more_frames_in_common", framesInCommon);
        }

        if (exception.getCause() != null) {
            m.put("cause", getStackTrace(exception.getCause(), trace));
        }
        return m;
    }

    private Response createContent(Map<String, Object> root, Exchange exchange) {
        Response.ResponseBuilder builder = Response.statusCode(statusCode);
        try {
            if (exchange != null && exchange.getRequest().isXML()) {
                createXMLContent(root, builder);
            } else {
                createJson(root, builder);
            }
        } catch (Exception e) {
            builder.body("Title: %s\nType: %s\n%s".formatted(title,type,root).getBytes(UTF_8));
            builder.contentType(TEXT_PLAIN_UTF8);
        }
        return builder.build();
    }

    private static void createXMLContent(Map<String, Object> root, Response.ResponseBuilder builder) throws Exception {
        builder.body(convertMapToXml(root));
        builder.contentType(APPLICATION_PROBLEM_XML);
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
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.transform(new DOMSource(document), new StreamResult(writer));
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
            throw new RuntimeException("No Content-Type in message with ProblemDetails!");

        if (!r.getHeader().getContentType().equals(APPLICATION_PROBLEM_JSON))
            throw new RuntimeException("Content-Type ist %s but should be %s.".formatted(r.getHeader().getContentType(), APPLICATION_PROBLEM_JSON));

        ProblemDetails pd = new ProblemDetails();
        pd.statusCode(r.getStatusCode());

        TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {
        };

        Map<String, Object> m = om.readValue(r.getBodyAsStringDecoded(), typeRef);

        pd.type = (String) m.get(TYPE);
        pd.title = (String) m.get(TITLE);
        pd.detail = (String) m.get(DETAIL);

        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (pd.isReservedProblemDetailsField(e.getKey()))
                continue;
            pd.internal(e.getKey(), e.getValue());
        }
        return pd;
    }

    private boolean isReservedProblemDetailsField(String key) {
        for (String reserved : List.of(TYPE, TITLE, DETAIL, INSTANCE)) {
            if (key.equals(reserved))
                return true;
        }
        return false;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isLogKeyInsteadOfDetails() {
        return logKeyInsteadOfDetails;
    }

    public String getDetail() {
        return detail;
    }

    public String getComponent() {
        return component;
    }

    public HashMap<String, Object> getInternal() {
        return internalFields;
    }

    public Throwable getException() {
        return exception;
    }

    public boolean isStacktrace() {
        return stacktrace;
    }


}