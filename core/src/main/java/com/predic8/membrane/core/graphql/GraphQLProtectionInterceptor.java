package com.predic8.membrane.core.graphql;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.graphql.model.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.URLParamUtil;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.security.InvalidParameterException;
import java.util.*;

import static com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description
 * Check GraphQL-over-HTTP requests, enforcing several limits and/or restrictions. This effectively helps to reduce
 * the attack surface.
 *
 * GraphQL Specification "October2021" is used. (But GraphQL only covers formulation of Documents/Queries.)
 *
 * GraphQL-over-HTTP, which specifies how to submit GraphQL queries via HTTP, has not been released/finalized yet. We
 * therefore use Version
 * <a href="https://github.com/graphql/graphql-over-http/blob/a1e6d8ca248c9a19eb59a2eedd988c204909ee3f/spec/GraphQLOverHTTP.md">a1e6d8ca</a>.
 *
 * Only GraphQL documents conforming to the 'ExecutableDocument' of the grammar are allowed: This includes the usual
 * 'query', 'mutation', 'subscription' and 'fragment's.
 */
@MCElement(name = "graphQLProtection")
public class GraphQLProtectionInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLProtectionInterceptor.class);

    private final GraphQLParser graphQLParser = new GraphQLParser();
    private final ObjectMapper om = new ObjectMapper()
            .configure(FAIL_ON_READING_DUP_TREE_KEY, true)
            .configure(STRICT_DUPLICATE_DETECTION, true);

    private boolean allowExtensions = false;
    private List<String> allowedMethods = Lists.newArrayList("GET", "POST");
    private int maxRecursion = 3;
    private int maxDepth = 7;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (!allowedMethods.contains(exc.getRequest().getMethod()))
            return error(exc, 405, "Invalid method.");

        Map data;
        String rawQuery = router.getUriFactory().create(exc.getRequest().getUri()).getRawQuery();
        if ("GET".equals(exc.getRequest().getMethod())) {
            if (rawQuery == null)
                return error(exc, "No query parameters found.");
            try {
                data = URLParamUtil.parseQueryString(rawQuery, ERROR);
            } catch (Exception e) {
                return error(exc, "Error decoding query string.");
            }
            if (data.containsKey("variables"))
                data.put("variables", om.readValue((String)data.get("variables"), Map.class));
            if (data.containsKey("extensions"))
                data.put("extensions", om.readValue((String)data.get("extensions"), Map.class));
        } else if ("POST".equals(exc.getRequest().getMethod())) {
            if (rawQuery != null) {
                Map<String, String> params = URLParamUtil.parseQueryString(rawQuery, ERROR);
                for (String key : new String[] { "query", "operationName", "variables", "extensions" })
                    if (params.containsKey(key))
                        return error(exc, "'" + key + "' is not allowed as query parameter while using POST.");
            }

            List<HeaderField> contentType = exc.getRequest().getHeader().getValues(new HeaderName(Header.CONTENT_TYPE));
            if (contentType.size() == 0)
                return error(exc, "No 'Content-Type' found.");
            if (contentType.size() > 1)
                return error(exc, "Found multiple 'Content-Type' headers.");
            ContentType ct;
            try {
                ct = new ContentType(contentType.get(0).getValue());
            } catch (ParseException e) {
                return error(exc, "Could not parse 'Content-Type' header.");
            }
            if (ct.match(MimeType.APPLICATION_GRAPHQL)) {
                data = ImmutableMap.of("query", exc.getRequest().getBodyAsStringDecoded());
            } else if (ct.match(MimeType.APPLICATION_JSON)) {

                String charset = ct.getParameter("charset");
                if (charset != null && !"utf-8".equalsIgnoreCase(charset))
                    return error(exc, "Invalid charset in 'Content-Type': Expected 'utf-8'.");

                try {
                    data = om.readValue(exc.getRequest().getBodyAsStreamDecoded(), Map.class);
                } catch (JsonParseException e) {
                    return error(exc, "Error decoding JSON object.");
                }
            } else {
                return error(exc, "Expected 'Content-Type: application/json' or 'Content-Type: application/graphql'.");
            }
        } else {
            exc.setResponse(Response.methodNotAllowed().build());
            return Outcome.RETURN;
        }

        Object query = data.get("query");
        if (query == null)
            return error(exc, "Parameter 'query' is missing.");
        if (!(query instanceof String))
            return error(exc, "Expected 'query' to be of type 'String'.");

        if (!allowExtensions && data.containsKey("extensions") && data.get("extensions") != null)
            return error(exc, "GraphQL 'extensions' are forbidden.");

        Object operationName = data.get("operationName");
        if (operationName != null) {
            if (!(operationName instanceof String))
                return error(exc, "Expected 'operationName' to be a String.");
        }

        Object variables = data.get("variables");
        if (variables != null) {
            if (!(variables instanceof Map))
                return error(exc, "Expected 'variables' to be a JSON Object.");
        }

        Object extensions = data.get("extensions");
        if (extensions != null) {
            if (!(extensions instanceof Map))
                return error(exc, "Expected 'extensions' to be a JSON Object.");
        }

        ExecutableDocument ed = graphQLParser.parseRequest(new ByteArrayInputStream(((String) query).getBytes(UTF_8)));

        // so far, this ensures uniqueness of global names
        List<String> e1 = new GraphQLValidator().validate(ed);
        if (e1 != null && e1.size() > 0)
            return error(exc, e1.get(0));

        if ("GET".equals(exc.getRequest().getMethod())) {
            if (ed.getExecutableDefinitions().stream()
                    .filter(exd -> exd instanceof OperationDefinition)
                    .map(exd -> (OperationDefinition) exd)
                    .anyMatch(od -> od.getOperationType() != null
                            && !"query".equals(od.getOperationType().getOperation())))
                return error(exc, 405, "'GET' may only be used for GraphQL 'query's.");
        }

        OperationDefinition operationToExecute = null;

        if (operationName != null && !operationName.equals("")) {
            List<OperationDefinition> ods = ed.getExecutableDefinitions().stream()
                    .filter(exd -> exd instanceof OperationDefinition)
                    .map(exd -> (OperationDefinition) exd)
                    .filter(od -> operationName.equals(od.getName())).toList();
            if (ods.size() == 0)
                return error(exc, "The operation named by 'operationName' could not be found.");
            if (ods.size() > 1)
                return error(exc, "Multiple OperationDefinitions with the same name in the GraphQL document.");
            operationToExecute = ods.get(0);
        } else {
            List<OperationDefinition> ods = ed.getExecutableDefinitions().stream()
                    .filter(exd -> exd instanceof OperationDefinition)
                    .map(exd -> (OperationDefinition) exd).toList();
            if (ods.size() == 0)
                return error(exc, "Could not find an OperationDefinition in the GraphQL document.");
            if (ods.size() > 1)
                return error(exc, "Multiple OperationDefinitions with the same name in the GraphQL document.");
            operationToExecute = ods.get(0);
        }

        String depthOrRecursionError = getDepthOrRecursionError(ed, operationToExecute);
        if (depthOrRecursionError != null)
            return error(exc, depthOrRecursionError);

        return Outcome.CONTINUE;
    }

    private String getDepthOrRecursionError(ExecutableDocument ed, OperationDefinition od) {
        String err = checkSelections(ed, od, od.getSelections(), new ArrayList<>(), new HashSet<>());
        if (err != null)
            return err;
        return null;
    }

    private String checkSelections(ExecutableDocument ed, OperationDefinition od, List<Selection> selections, List<String> fieldStack, HashSet<String> fragmentNamesVisited) {
        if (selections == null)
            return null;
        for (Selection selection : selections) {
            if (selection == null) {
                LOG.error("Selection is null.");
                return "See server log.";
            }
            String err = switch(selection) {
                case Field field -> checkField(field, ed, od, selections, fieldStack, fragmentNamesVisited);
                case FragmentSpread fs -> checkFragmentSpread(fs, ed, od, selections, fieldStack, fragmentNamesVisited);
                case InlineFragment ifr -> checkSelections(ed, od, ifr.getSelections(), fieldStack, fragmentNamesVisited);
                default -> checkUnhandled(selection);
            };
            if (err != null)
                return err;
        }
        return null;
    }

    private String checkUnhandled(Selection selection) {
        LOG.error("Unhandled class: " + selection.getClass().getName());
        return "See server log.";
    }

    private String checkFragmentSpread(FragmentSpread fragmentSpread, ExecutableDocument ed, OperationDefinition od, List<Selection> selections, List<String> fieldStack, HashSet<String> fragmentNamesVisited) {
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

    private String checkField(Field field, ExecutableDocument ed, OperationDefinition od, List<Selection> selections, List<String> fieldStack, HashSet<String> fragmentNamesVisited) {
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
        return Outcome.RETURN;
    }

    private Outcome error(Exchange exc, int code, String message) {
        LOG.warn(message);
        exc.setResponse(Response.badRequest().status(code).build());
        return Outcome.RETURN;
    }


    public boolean isAllowExtensions() {
        return allowExtensions;
    }

    /**
     * Whether to allow GraphQL "extensions".
     * @default false
     * @example true
     */
    @MCAttribute
    public void setAllowExtensions(boolean allowExtensions) {
        this.allowExtensions = allowExtensions;
    }

    public String getAllowedMethods() {
        return String.join(",", allowedMethods);
    }

    /**
     * Which HTTP methods to allow. Note, that per the GraphQL-over-HTTP spec, you need POST for mutation or subscription queries.
     * @default GET,POST
     */
    @MCAttribute
    public void setAllowedMethods(String allowedMethods) {
        this.allowedMethods = Arrays.asList(allowedMethods.split(","));
        for (String allowedMethod : this.allowedMethods)
            if (!"GET".equals(allowedMethod) && !"POST".equals(allowedMethod))
                throw new InvalidParameterException("<graphQLProtectionInterceptor allowedMethods=\"...\" /> may only allow GET or POST.");
    }
}
