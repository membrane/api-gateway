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

import com.google.common.collect.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.graphql.blocklist.FeatureBlocklist;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.io.IOException;
import java.security.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.METHOD_GET;
import static com.predic8.membrane.core.http.Request.METHOD_POST;
import static com.predic8.membrane.core.interceptor.Outcome.*;

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
 * @topic 3. Security and Validation
 */
@SuppressWarnings("unused")
@MCElement(name = "graphQLProtection")
public class GraphQLProtectionInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLProtectionInterceptor.class);

    private boolean allowExtensions = false;
    private List<String> allowedMethods = Lists.newArrayList("GET", "POST");
    private int maxRecursion = 3;
    private int maxDepth = 7;
    private int maxMutations = 5;
    private FeatureBlocklist featureBlocklist;

    private GraphQLoverHttpValidator validator;

    public GraphQLProtectionInterceptor() {
        name = "graphql protection";
    }

    @Override
    public void init() {
        super.init();
        validator = new GraphQLoverHttpValidator( allowExtensions, allowedMethods,  maxRecursion,  maxDepth,  maxMutations, featureBlocklist, router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            validator.validate(exc);
        } catch (GraphQLOverHttpValidationException e) {
            return error(exc, e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return CONTINUE;
    }

    private Outcome error(Exchange exc, GraphQLOverHttpValidationException e) {
        LOG.warn(e.getMessage());
        exc.setResponse(Response.badRequest().status(e.getStatusCode()).build());
        return RETURN;
    }

    /**
     * @description Limit how many mutations can be defined in a document query.
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
     * @description Whether to allow GraphQL "extensions".
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
     * @description Which HTTP methods to allow. Note that per the GraphQL-over-HTTP spec, you need POST for mutation or subscription queries.
     * @default GET, POST
     */
    @MCAttribute
    public void setAllowedMethods(String allowedMethods) {
        this.allowedMethods = Arrays.asList(allowedMethods.split(","));
        for (String allowedMethod : this.allowedMethods)
            if (!METHOD_GET.equals(allowedMethod) && !METHOD_POST.equals(allowedMethod))
                throw new InvalidParameterException("<graphQLProtectionInterceptor allowedMethods=\"...\" /> may only allow GET or POST.");
    }

    public int getMaxRecursion() {
        return maxRecursion;
    }

    /**
     * @description How deep recursive parts of queries can be nested.
     * @default 3
     */
    @MCAttribute
    public void setMaxRecursion(int maxRecursion) {
        this.maxRecursion = maxRecursion;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * @description How deep queries can be nested.
     * @default 7
     */
    @MCAttribute
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public FeatureBlocklist getBlacklist() {return featureBlocklist;}

    /**
     * @description Block GraphL features like mutations, introspection, and subscriptions.
     * @default not present
     */
    @MCChildElement
    public void setBlocklist(FeatureBlocklist featureBlocklist) {this.featureBlocklist = featureBlocklist;}

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
