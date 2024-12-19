package com.predic8.membrane.core.graphql;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.graphql.model.*;
import com.predic8.membrane.core.http.*;
import jakarta.mail.internet.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

import static com.fasterxml.jackson.core.JsonParser.Feature.*;
import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static java.nio.charset.StandardCharsets.*;

public class GraphQLoverHttpValidator {
    private static final Logger log = LoggerFactory.getLogger(GraphQLoverHttpValidator.class);

    public static final String EXTENSIONS = "extensions";
    public static final String VARIABLES = "variables";
    public static final String MUTATION = "mutation";
    public static final String QUERY = "query";
    public static final String OPERATION_NAME = "operationName";

    private final GraphQLParser graphQLParser = new GraphQLParser();
    private final ObjectMapper om = new ObjectMapper()
            .configure(FAIL_ON_READING_DUP_TREE_KEY, true)
            .configure(STRICT_DUPLICATE_DETECTION, true);

    private final boolean allowExtensions;
    private final List<String> allowedMethods;
    private final int maxRecursion;
    private final int maxDepth;
    private final int maxMutations;
    private final Router router;

    public GraphQLoverHttpValidator(boolean allowExtensions, List<String> allowedMethods, int maxRecursion, int maxDepth, int maxMutations, Router router) {
        this.allowExtensions = allowExtensions;
        this.allowedMethods = allowedMethods;
        this.maxRecursion = maxRecursion;
        this.maxDepth = maxDepth;
        this.maxMutations = maxMutations;
        this.router = router;
    }

    public void validate(Exchange exc) throws GraphQLOverHttpValidationException {
        if (!allowedMethods.contains(exc.getRequest().getMethod()))
            throw new GraphQLOverHttpValidationException(405, "Invalid method.");

        Map<String, Object> data = getData(exc);
        checkExtensions(data);
        checkVariables(data);
        checkExtension(data);

        ExecutableDocument ed = getExecutableDocument(getQuery(data));
        checkMutations(ed);
        validate(ed);

        checkThatGetIsUsedOnlyForQueries(exc, ed);
        checkDepthOrRecursion(ed, getOperationName(data));
    }

    private void checkThatGetIsUsedOnlyForQueries(Exchange exc, ExecutableDocument ed) {
        if (!exc.getRequest().isGETRequest())
            return;

        if (ed.getOperationDefinitions().stream().anyMatch(od -> od.getOperationType() != null
                                && !QUERY.equals(od.getOperationType().getOperation())))
            throw new GraphQLOverHttpValidationException(405, "'GET' may only be used for GraphQL 'query's.");
    }

    private static @NotNull Predicate<ExecutableDefinition> isOperationDefinition() {
        return exd -> exd instanceof OperationDefinition;
    }

    private void checkMutations(ExecutableDocument ed) {
        if (countMutations(ed.getExecutableDefinitions()) > maxMutations)
            throw new GraphQLOverHttpValidationException("Too many mutations defined in document.");
    }

    private void checkExtensions(Map<String, Object> data) {
        if (!allowExtensions && data.containsKey(EXTENSIONS) && data.get(EXTENSIONS) != null)
            throw new GraphQLOverHttpValidationException("GraphQL 'extensions' are forbidden.");
    }

    private @NotNull Map<String, Object> getData(Exchange exc) {
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
            throw new GraphQLOverHttpValidationException(depthOrRecursionError);
    }

    private static @Nullable Object getOperationName(Map data) {
        Object operationName = data.get(OPERATION_NAME);
        if (operationName != null) {
            if (!(operationName instanceof String))
                throw new GraphQLOverHttpValidationException("Expected 'operationName' to be a String.");
        }
        return operationName;
    }

    private static void checkVariables(Map data) {
        Object variables = data.get(VARIABLES);
        if (variables != null) {
            if (!(variables instanceof Map))
                throw new GraphQLOverHttpValidationException("Expected 'variables' to be a JSON Object.");
        }
    }

    private static void validate(ExecutableDocument ed) {
        // so far, this ensures uniqueness of global names
        List<String> e1 = new GraphQLValidator().validate(ed);
        if (e1 != null && !e1.isEmpty())
            throw new GraphQLOverHttpValidationException(e1.get(0));
    }

    private static OperationDefinition getOperationDefinition(Object operationName, ExecutableDocument ed) {
        if (operationName != null && !operationName.equals("")) {
            List<OperationDefinition> ods = ed.getOperationDefinitionsByName(operationName);
            if (ods.isEmpty())
                throw new GraphQLOverHttpValidationException("The operation named by 'operationName' could not be found.");
            if (ods.size() > 1)
                throw new GraphQLOverHttpValidationException("Multiple OperationDefinitions with the same name in the GraphQL document.");
            return ods.get(0);
        }
        List<OperationDefinition> ods = ed.getOperationDefinitions();
        if (ods.isEmpty())
            throw new GraphQLOverHttpValidationException("Could not find an OperationDefinition in the GraphQL document.");
        return ods.get(0);
    }

    private ExecutableDocument getExecutableDocument(String query) {
        try {
            return graphQLParser.parseRequest(new ByteArrayInputStream(query.getBytes(UTF_8)));
        } catch (Exception e) {
            log.debug("Error parsing GraphQL request", e);
            throw new GraphQLOverHttpValidationException(422, "Error parsing GraphQL request.");
        }
    }

    private static void checkExtension(Map data) {
        Object extensions = data.get(EXTENSIONS);
        if (extensions == null)
            return;
        if (!(extensions instanceof Map))
            throw new GraphQLOverHttpValidationException("Expected 'extensions' to be a JSON Object.");

    }

    private static @NotNull String getQuery(Map data) {
        Object query = data.get(QUERY);
        if (query == null)
            throw new GraphQLOverHttpValidationException("Parameter 'query' is missing.");
        if (!(query instanceof String))
            throw new GraphQLOverHttpValidationException("Expected 'query' to be of type 'String'.");
        return (String) query;
    }

    private @NotNull Map<String, Object> getDataPost(Exchange exc, String rawQuery) {
        if (rawQuery != null) {
            Map<String, String> params = parseQueryString(rawQuery, ERROR);
            for (String key : new String[]{QUERY, OPERATION_NAME, VARIABLES, EXTENSIONS})
                if (params.containsKey(key))
                    throw new GraphQLOverHttpValidationException("'" + key + "' is not allowed as query parameter while using POST.");
        }

        ContentType ct = getContentType2(exc);
        if (ct.match(APPLICATION_GRAPHQL)) {
            return ImmutableMap.of(QUERY, exc.getRequest().getBodyAsStringDecoded());
        }
        if (ct.match(APPLICATION_JSON)) {
            String charset = ct.getParameter("charset");
            if (charset != null && !"utf-8".equalsIgnoreCase(charset))
                throw new GraphQLOverHttpValidationException("Invalid charset in 'Content-Type': Expected 'utf-8'.");
            try {
                return om.readValue(exc.getRequest().getBodyAsStreamDecoded(), Map.class);
            } catch (Exception e) {
                throw new GraphQLOverHttpValidationException("Error decoding JSON object.");
            }
        }
        throw new GraphQLOverHttpValidationException("Expected 'Content-Type: application/json' or 'Content-Type: application/graphql'.");
    }

    private static @NotNull ContentType getContentType2(Exchange exc) {
        List<HeaderField> contentType = exc.getRequest().getHeader().getValues(new HeaderName(CONTENT_TYPE));
        if (contentType.isEmpty())
            throw new GraphQLOverHttpValidationException("No 'Content-Type' found.");
        if (contentType.size() > 1)
            throw new GraphQLOverHttpValidationException("Found multiple 'Content-Type' headers.");
        return getContentType(contentType);
    }

    private static @NotNull ContentType getContentType(List<HeaderField> contentType) {
        try {
            return new ContentType(contentType.get(0).getValue());
        } catch (ParseException e) {
            throw new GraphQLOverHttpValidationException("Could not parse 'Content-Type' header.");
        }
    }

    private String getRawQuery(Exchange exc) {
        try {
            return router.getUriFactory().create(exc.getRequest().getUri()).getRawQuery();
        } catch (URISyntaxException e) {
            throw new GraphQLOverHttpValidationException(400, "Invalid request URI.");
        }
    }

    private @NotNull Map<String, Object> getData(String rawQuery) {
        Map data;
        if (rawQuery == null)
            throw new GraphQLOverHttpValidationException("No query parameters found.");
        try {
            data = parseQueryString(rawQuery, ERROR);
        } catch (Exception e) {
            throw new GraphQLOverHttpValidationException("Error decoding query string.");
        }
        try {
            if (data.containsKey(VARIABLES))
                data.put(VARIABLES, om.readValue((String) data.get(VARIABLES), Map.class));
            if (data.containsKey(EXTENSIONS))
                data.put(EXTENSIONS, om.readValue((String) data.get(EXTENSIONS), Map.class));
            return data;
        } catch (JsonProcessingException e) {
            throw new GraphQLOverHttpValidationException(422, "Error parsing variables or extensions from request JSON.");
        }
    }

    public static int countMutations(List<ExecutableDefinition> definitions) {
        return (int) getMutationOperations(definitions).map(OperationDefinition::getSelections).mapToLong(List::size).sum();
    }

    private static @NotNull Stream<OperationDefinition> getMutationOperations(List<ExecutableDefinition> definitions) {
        return definitions.stream()
                .filter(isOperationDefinition())
                .map(definition -> (OperationDefinition) definition)
                .filter(operation -> operation.getOperationType() != null)
                .filter(GraphQLoverHttpValidator::isMutation);
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
                log.error("Selection is null.");
                return "See server log.";
            }

            // @TODO Replace with polymorphism
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
        log.error("Unhandled class: " + selection.getClass().getName());
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

    // @TODO
    //  - Make testable
    //  - return void
    //  - case of error throw exception
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
}
