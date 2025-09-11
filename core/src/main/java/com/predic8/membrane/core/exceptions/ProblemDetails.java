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
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetailsXML.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.util.ExceptionUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Locale.*;
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
    public static final String STATUS = "status";
    public static final String INTERNAL = "internal";
    public static final String INTERNAL_SERVER_ERROR = "Internal Server Error";

    private static final Set<String> RESERVED = Set.of(TYPE, TITLE, DETAIL, INSTANCE, STATUS);
    public static final String MESSAGE = "message";
    public static final String STACK_TRACE = "stackTrace";
    public static final String LOG_KEY = "logKey";

    /**
     * If router is in production mode that should not expose internal details
     */
    private boolean production;

    protected int status;
    protected String type;
    protected String subType = "";

    protected String title;
    protected String detail;

    protected Interceptor.Flow flow;
    protected String seeSuffix = "";

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

    public static ProblemDetails user(boolean production, String component) {
        return problemDetails("user", production)
                .status(400)
                .title("User error.")
                .component(component);
    }

    public static ProblemDetails internal(boolean production, String component) {
        return problemDetails(INTERNAL, production)
                .status(500)
                .title("Internal server error.")
                .component(component);
    }

    public static ProblemDetails gateway(boolean production, String component) {
        return problemDetails("gateway", production)
                .status(500)
                .title("Gateway error.")
                .component(component);
    }

    public static ProblemDetails security(boolean production, String component) {
        return problemDetails("security", production)
                .status(500)
                .title("Security error.")
                .component(component);
    }

    public static ProblemDetails openapi(boolean production, String component) {
        return problemDetails("openapi", production)
                .status(400)
                .title("OpenAPI error.")
                .component(component);
    }

    public static ProblemDetails problemDetails(String type, boolean production) {
        ProblemDetails pd = new ProblemDetails();
        pd.type = type;
        pd.production = production;
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

    public ProblemDetails status(int statusCode) {
        this.status = statusCode;
        return this;
    }

    public ProblemDetails title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Should not be set from the typical user. Method is for tests.
     */
    protected ProblemDetails type(String type) {
        this.type = type;
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

    /**
     * Hide in production mode
     */
    public ProblemDetails internal(String key, Object value) {
        this.internalFields.put(key, value);
        return this;
    }

    public ProblemDetails topLevel(String key, Object value) {
        if (isReservedProblemDetailsField(key)) {
            log.warn("Ignoring topLevel field '{}': reserved by RFC 7807.", key);
            return this;
        }
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

        root.put(TITLE, maskTitle(title));
        root.put(TYPE, getTypeSubtypeString());
        root.put(STATUS, status);
        root.putAll(topLevel);

        if (production) {
            provideLogKeyInsteadOfDetails(root);
            return root;
        }

        if (detail != null) {
            root.put(DETAIL, detail);
        }

        root.putAll(createInternal(getTypeSubtypeString()));
        return root;
    }

    private String maskTitle(String title) {
        if (production && status >= 500)
            return INTERNAL_SERVER_ERROR;
        return title;
    }

    private void provideLogKeyInsteadOfDetails(Map<String, Object> root) {
        if (internalFields.isEmpty() && exception == null)
            return;

        String logKey = randomUUID().toString();

        try {
            MDC.put(LOG_KEY, logKey);
            log.info("ProblemDetails hidden. type={}, title={}, detail={}, internal={}",
                    getTypeSubtypeString(), title, detail, internalFields);
            if (exception != null) {
                log.info("Message={}", exception.getMessage());
                if (stacktrace) {
                    log.info("Stacktrace for hidden details:", exception);
                }
            }
        } finally {
            MDC.remove(LOG_KEY);
        }
        root.put(DETAIL, "Internal details are hidden. See server log (key: %s)".formatted(logKey));
    }

    private @NotNull String getTypeSubtypeString() {
        String type = "https://membrane-api.io/problems/" + this.type;
        if ((!production || (status >= 400 && status < 500)) && !subType.isEmpty()) {
            return  type + subType;
        }
        return type;
    }

    private String normalizeForType(String s) {
        return s.replace(" ", "-").toLowerCase(ROOT);
    }

    private Map<String, Object> createInternal(String type) {
        var internalMap = new LinkedHashMap<>(internalFields);
        if (exception != null) {
            if (internalMap.containsKey(MESSAGE))
                log.error("Overriding ProblemDetails extensionsMap 'message' entry. Please notify Membrane developers.", new RuntimeException());
            internalMap.put(MESSAGE, concatMessageAndCauseMessages(exception));
            if (stacktrace) {
                internalMap.put(STACK_TRACE, getStackTrace(exception, new StackTraceElement[0]));
            }
        }
        internalMap.put(SEE, getFullType(type));
        internalMap.put(ATTENTION, """
                Membrane is in development mode. For production set <router production="true"> to reduce details in error messages!""");
        return internalMap;
    }

    private String getFullType(String type) {
        StringBuilder sb = new StringBuilder(type);
        if (component != null && !component.isEmpty()) {
            sb.append('/').append(normalizeForType(component));
        }
        if (flow != null) {
            sb.append('/').append(flow.name().toLowerCase(ROOT));
        }
        if (!seeSuffix.isEmpty()) {
            sb.append('/').append(seeSuffix);
        }
        return sb.toString();
    }


    private static @NotNull Map<String, Object> getStackTrace(Throwable exception, StackTraceElement[] enclosingTrace) {
        var m = new LinkedHashMap<String,Object>();

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
        Response.ResponseBuilder builder = statusCode(status);
        try {
            if (exchange != null && (acceptXML(exchange) || exchange.getRequest().isXML())) {
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

    private boolean acceptXML(Exchange exchange) {
        String accept = exchange.getRequest().getHeader().getAccept();
        if (accept == null)
            return false;
        return accept.toLowerCase().contains("xml");
    }

    private static void createJson(Map<String, Object> root, Response.ResponseBuilder builder) throws JsonProcessingException {
        builder.body(ow.writeValueAsBytes(root));
        builder.contentType(APPLICATION_PROBLEM_JSON);
    }

    public static boolean isReservedProblemDetailsField(String key) {
        return RESERVED.contains(key);
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public int getStatus() {
        return status;
    }

    public boolean isProduction() {
        return production;
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