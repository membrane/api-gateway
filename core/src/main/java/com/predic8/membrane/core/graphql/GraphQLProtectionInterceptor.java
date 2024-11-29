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

package com.predic8.membrane.core.graphql;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.graphql.model.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import jakarta.mail.internet.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;

import static com.fasterxml.jackson.core.JsonParser.Feature.*;
import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Check GraphQL-over-HTTP requests, enforcing several limits and/or restrictions. This effectively helps to reduce
 * the attack surface.
 * <p>
 * GraphQL Specification "October2021" is used. (But GraphQL only covers formulation of Documents/Queries.)
 * </p>
 * <p>
 * GraphQL-over-HTTP, which specifies how to submit GraphQL queries via HTTP, has not been released/finalized yet. We
 * therefore use Version
 * <a href="https://github.com/graphql/graphql-over-http/blob/a1e6d8ca248c9a19eb59a2eedd988c204909ee3f/spec/GraphQLOverHTTP.md">a1e6d8ca</a>.
 * </p>
 * <p>
 * Only GraphQL documents conforming to the 'ExecutableDocument' of the grammar are allowed: This includes the usual
 * 'query', 'mutation', 'subscription' and 'fragment's.
 * </p>
 */
@SuppressWarnings("unused")
@MCElement(name = "graphQLProtection")
public class GraphQLProtectionInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLProtectionInterceptor.class);
    public static final String EXTENSIONS = "extensions";
    public static final String VARIABLES = "variables";
    public static final String MUTATION = "mutation";

    private final GraphQLParser graphQLParser = new GraphQLParser();
    private final ObjectMapper om = new ObjectMapper()
            .configure(FAIL_ON_READING_DUP_TREE_KEY, true)
            .configure(STRICT_DUPLICATE_DETECTION, true);

    private boolean allowExtensions = false;
    private List<String> allowedMethods = Lists.newArrayList("GET", "POST");
    private int maxRecursion = 3;
    private int maxDepth = 7;
    private int maxMutations = 5;

    public GraphQLProtectionInterceptor() {
        name = "GraphQL protection";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        try {
            return handleRequestInternal(exc);
        } catch (Exception e) {
            return error(exc, e.getMessage());
        }

    }


    public Outcome handleRequestInternal(Exchange exc) throws Exception {
        if (!allowedMethods.contains(exc.getRequest().getMethod()))
            return error(exc, 405, "Invalid method.");

        Map data = getData(exc);
        checkExtensions(data);
        checkVariables(data);
        checkExtension(data);

        ExecutableDocument ed = getExecutableDocument(getQuery(data));
        checkMutations(ed);
        validate(ed);

        if (exc.getRequest().isGETRequest()) {
            if (ed.getExecutableDefinitions().stream()
                    .filter(exd -> exd instanceof OperationDefinition)
                    .map(exd -> (OperationDefinition) exd)
                    .anyMatch(od -> od.getOperationType() != null
                                    && !"query".equals(od.getOperationType().getOperation())))
                return error(exc, 405, "'GET' may only be used for GraphQL 'query's.");
        }

        checkDepthOrRecursion(ed, getOperationName(data));
        return CONTINUE;
    }

    private void checkMutations(ExecutableDocument ed) {
        if (countMutations(ed.getExecutableDefinitions()) > maxMutations)
            throw new RuntimeException("Too many mutations defined in document.");
    }

    private void checkExtensions(Map data) {
        if (!allowExtensions && data.containsKey(EXTENSIONS) && data.get(EXTENSIONS) != null)
            throw new RuntimeException("GraphQL 'extensions' are forbidden.");
    }

    private @Nullable Map<String,Object> getData(Exchange exc) throws URISyntaxException, IOException {
        if (exc.getRequest().isGETRequest()) {
            return getData(getRawQuery(exc));
        }
        if (exc.getRequest().isPOSTRequest()) {
            return getDataPost(exc, getRawQuery(exc));
        }
        throw new IllegalStateException("Should never get here");
    }

    private void checkDepthOrRecursion(ExecutableDocument ed, Object operationName) {
        String depthOrRecursionError = getDepthOrRecursionError(ed, getOperationDefinition(operationName, ed));
        if (depthOrRecursionError != null)
            throw new RuntimeException(depthOrRecursionError);
    }

    private static @Nullable Object getOperationName(Map data) {
        Object operationName = data.get("operationName");
        if (operationName != null) {
            if (!(operationName instanceof String))
                throw new RuntimeException("Expected 'operationName' to be a String.");
        }
        return operationName;
    }

    private static void checkVariables(Map data) {
        Object variables = data.get(VARIABLES);
        if (variables != null) {
            if (!(variables instanceof Map))
                throw new RuntimeException("Expected 'variables' to be a JSON Object.");
        }
    }

    private static void validate(ExecutableDocument ed) {
        // so far, this ensures uniqueness of global names
        List<String> e1 = new GraphQLValidator().validate(ed);
        if (e1 != null && !e1.isEmpty())
            throw new RuntimeException(e1.get(0));
    }

    private static OperationDefinition getOperationDefinition(Object operationName, ExecutableDocument ed) {
        if (operationName != null && !operationName.equals("")) {
            List<OperationDefinition> ods = ed.getExecutableDefinitions().stream()
                    .filter(exd -> exd instanceof OperationDefinition)
                    .map(exd -> (OperationDefinition) exd)
                    .filter(od -> operationName.equals(od.getName())).toList();
            if (ods.isEmpty())
                throw new RuntimeException("The operation named by 'operationName' could not be found.");
            if (ods.size() > 1)
                throw new RuntimeException("Multiple OperationDefinitions with the same name in the GraphQL document.");
            return ods.get(0);
        }
        List<OperationDefinition> ods = getOperationDefinitions(ed);
        if (ods.isEmpty())
            throw new RuntimeException("Could not find an OperationDefinition in the GraphQL document.");
        return ods.get(0);
    }

    private static @NotNull List<OperationDefinition> getOperationDefinitions(ExecutableDocument ed) {
        return ed.getExecutableDefinitions().stream()
                .filter(exd -> exd instanceof OperationDefinition)
                .map(exd -> (OperationDefinition) exd).toList();
    }

    private ExecutableDocument getExecutableDocument(String query) throws IOException, ParsingException {
        return graphQLParser.parseRequest(new ByteArrayInputStream(query.getBytes(UTF_8)));
    }

    private static void checkExtension(Map data) {
        Object extensions = data.get(EXTENSIONS);

        if (extensions == null)
            return;

        if (!(extensions instanceof Map))
            throw new RuntimeException("Expected 'extensions' to be a JSON Object.");

    }

    private static @NotNull String getQuery(Map data) {
        Object query = data.get("query");
        if (query == null)
            throw new RuntimeException("Parameter 'query' is missing.");
        if (!(query instanceof String))
            throw new RuntimeException("Expected 'query' to be of type 'String'.");
        return (String) query;
    }

    private Map<String,Object> getDataPost(Exchange exc, String rawQuery) throws IOException {

        if (rawQuery != null) {
            Map<String, String> params = URLParamUtil.parseQueryString(rawQuery, ERROR);
            for (String key : new String[]{"query", "operationName", VARIABLES, EXTENSIONS})
                if (params.containsKey(key))
                    throw new RuntimeException("'" + key + "' is not allowed as query parameter while using POST.");
        }

        ContentType ct = getContentType2(exc);

        if (ct.match(MimeType.APPLICATION_GRAPHQL)) {
            return ImmutableMap.of("query", exc.getRequest().getBodyAsStringDecoded());
        }

        if (ct.match(MimeType.APPLICATION_JSON)) {
            String charset = ct.getParameter("charset");
            if (charset != null && !"utf-8".equalsIgnoreCase(charset))
                throw new RuntimeException("Invalid charset in 'Content-Type': Expected 'utf-8'.");

            try {
                return om.readValue(exc.getRequest().getBodyAsStreamDecoded(), Map.class);
            } catch (JsonParseException e) {
                throw new RuntimeException("Error decoding JSON object.");
            }
        }
        throw new RuntimeException("Expected 'Content-Type: application/json' or 'Content-Type: application/graphql'.");
    }

    private static @NotNull ContentType getContentType2(Exchange exc) {
        List<HeaderField> contentType = exc.getRequest().getHeader().getValues(new HeaderName(CONTENT_TYPE));
        if (contentType.isEmpty())
            throw new RuntimeException("No 'Content-Type' found.");
        if (contentType.size() > 1)
            throw new RuntimeException("Found multiple 'Content-Type' headers.");
        return getContentType(contentType);
    }

    private static @NotNull ContentType getContentType(List<HeaderField> contentType) {
        try {
            return new ContentType(contentType.get(0).getValue());
        } catch (ParseException e) {
            throw new RuntimeException("Could not parse 'Content-Type' header.");
        }
    }

    private String getRawQuery(Exchange exc) throws URISyntaxException {
        return router.getUriFactory().create(exc.getRequest().getUri()).getRawQuery();
    }

    private @NotNull Map<String,Object> getData(String rawQuery) throws JsonProcessingException {
        Map data;
        if (rawQuery == null)
            throw new RuntimeException("No query parameters found.");
        try {
            data = URLParamUtil.parseQueryString(rawQuery, ERROR);
        } catch (Exception e) {
            throw new RuntimeException("Error decoding query string.");
        }
        if (data.containsKey(VARIABLES))
            data.put(VARIABLES, om.readValue((String) data.get(VARIABLES), Map.class));
        if (data.containsKey(EXTENSIONS))
            data.put(EXTENSIONS, om.readValue((String) data.get(EXTENSIONS), Map.class));
        return data;
    }

    public int countMutations(List<ExecutableDefinition> definitions) {
        return (int) definitions.stream()
                .filter(definition -> definition instanceof OperationDefinition)
                .map(definition -> (OperationDefinition) definition)
                .filter(operation -> operation.getOperationType() != null)
                .filter(GraphQLProtectionInterceptor::isMutation)
                .count();
    }

    private static boolean isMutation(OperationDefinition operation) {
        return operation.getOperationType().getOperation().equals(MUTATION);
    }

    private String getDepthOrRecursionError(ExecutableDocument ed, OperationDefinition od) {
        return checkSelections(ed, od, od.getSelections(), new ArrayList<>(), new HashSet<>());
    }

    private String checkSelections(ExecutableDocument ed, OperationDefinition od, List<Selection> selections, List<String> fieldStack, HashSet<String> fragmentNamesVisited) {
        if (selections == null)
            return null;
        for (Selection selection : selections) {
            if (selection == null) {
                LOG.error("Selection is null.");
                return "See server log.";
            }
            if (selection instanceof Field) {
                return checkField((Field) selection, ed, od, fieldStack, fragmentNamesVisited);
            }
            if (selection instanceof FragmentSpread) {
                return checkFragmentSpread((FragmentSpread) selection, ed, od, fieldStack, fragmentNamesVisited);
            }
            if (selection instanceof InlineFragment) {
                return checkSelections(ed, od, ((InlineFragment) selection).getSelections(), fieldStack, fragmentNamesVisited);
            }
            return checkUnhandled(selection);
        }
        return null;
    }

    private String checkUnhandled(Selection selection) {
        LOG.error("Unhandled class: " + selection.getClass().getName());
        return "See server log.";
    }

    private String checkFragmentSpread(FragmentSpread fragmentSpread, ExecutableDocument ed, OperationDefinition od, List<String> fieldStack, HashSet<String> fragmentNamesVisited) {
        String fragmentName = fragmentSpread.getFragmentName();

        Optional<FragmentDefinition> fragment = ed.getExecutableDefinitions().stream()
                .filter(ed2 -> ed2 instanceof FragmentDefinition)
                .map(ed2 -> (FragmentDefinition) ed2)
                .filter(ed2 -> fragmentName.equals(ed2.getName()))
                .findAny();

        if (fragment.isEmpty())
            return "Did not find fragment '" + fragmentName + "'.";

        if (!fragmentNamesVisited.add(fragmentName))
            return "Fragment spreads form cycle ('" + fragmentName + "').";
        String err = checkSelections(ed, od, fragment.get().getSelections(), fieldStack, fragmentNamesVisited);
        if (err != null)
            return err;
        fragmentNamesVisited.remove(fragmentName);
        return null;
    }

    private String checkField(Field field, ExecutableDocument ed, OperationDefinition od, List<String> fieldStack, HashSet<String> fragmentNamesVisited) {
        String fieldName = field.getName();
        fieldStack.add(fieldName);
        if (fieldStack.size() > maxDepth)
            return "Max depth exceeded.";
        if (fieldStack.stream().filter(name -> name.equals(fieldName)).count() > maxRecursion)
            return "Max recursion exceeded.";

        String err = checkSelections(ed, od, field.getSelections(), fieldStack, fragmentNamesVisited);
        if (err != null)
            return err;
        fieldStack.remove(fieldStack.size() - 1);
        return null;
    }

    private Outcome error(Exchange exc, String message) {
        LOG.warn(message);
        exc.setResponse(Response.badRequest().build());
        return RETURN;
    }

    private Outcome error(Exchange exc, int code, String message) {
        LOG.warn(message);
        exc.setResponse(Response.badRequest().status(code).build());
        return RETURN;
    }


    /**
     * Limit how many mutations can be defined in a document query.
     *
     * @default 5
     * @example 2
     */
    @MCAttribute
    public void setMaxMutations(int maxMutations) {
        this.maxMutations = maxMutations;
    }

    @SuppressWarnings("unused")
    public int getMaxMutations() {
        return maxMutations;
    }

    /**
     * Whether to allow GraphQL "extensions".
     *
     * @default false
     * @example true
     */
    @MCAttribute
    public void setAllowExtensions(boolean allowExtensions) {
        this.allowExtensions = allowExtensions;
    }

    @SuppressWarnings("unused")
    public boolean isAllowExtensions() {
        return allowExtensions;
    }

    public String getAllowedMethods() {
        return String.join(",", allowedMethods);
    }

    /**
     * Which HTTP methods to allow. Note, that per the GraphQL-over-HTTP spec, you need POST for mutation or subscription queries.
     *
     * @default GET, POST
     */
    @MCAttribute
    public void setAllowedMethods(String allowedMethods) {
        this.allowedMethods = Arrays.asList(allowedMethods.split(","));
        for (String allowedMethod : this.allowedMethods)
            if (!"GET".equals(allowedMethod) && !"POST".equals(allowedMethod))
                throw new InvalidParameterException("<graphQLProtectionInterceptor allowedMethods=\"...\" /> may only allow GET or POST.");
    }

    public int getMaxRecursion() {
        return maxRecursion;
    }

    @MCAttribute
    public void setMaxRecursion(int maxRecursion) {
        this.maxRecursion = maxRecursion;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    @MCAttribute
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public String toString() {
        return "GraphQL protection";
    }

    @Override
    public String getShortDescription() {
        return "Let only well-formed GraphQL requests pass. Apply restrictions.";
    }

    @Override
    public String getLongDescription() {
        return
                "<div>Protects against some GraphQL attack classes (checks HTTP request against <a href=\"https://" +
                "spec.graphql.org/October2021/\">GraphQL</a> and <a href=\"https://github.com/graphql/" +
                "graphql-over-http/blob/a1e6d8ca248c9a19eb59a2eedd988c204909ee3f/spec/GraphQLOverHTTP.md\">" +
                "GraphQL-over-HTTP</a> specs).<br/>" +
                "GraphQL extensions: " + (allowExtensions ? "Allowed." : "Forbidden.") + "<br/>" +
                "Allowed HTTP verbs: " + TextUtil.toEnglishList("and", allowedMethods.toArray(new String[0])) + ".<br/>" +
                "Maximum allowed nested query levels: " + maxDepth + "<br/>" +
                "Maximum allowed recursion levels (nested repetitions of the same word): " + maxRecursion + ".</div>";
    }
}
