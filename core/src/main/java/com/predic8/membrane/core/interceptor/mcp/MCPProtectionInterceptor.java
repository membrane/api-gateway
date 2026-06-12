package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.mcp.MCPProtectionValidator.ValidationError;
import com.predic8.membrane.core.util.config.allowdeny.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.METHOD_POST;
import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_INVALID_REQUEST;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.error;
import static java.util.EnumSet.of;

/**
 * @description
 * <p>Protects an MCP endpoint by validating incoming JSON-RPC requests and
 * restricting the MCP methods and tools that clients may use.</p>
 *
 * <p>Only HTTP <code>POST</code> requests with an
 * <code>application/json</code> content type are accepted. JSON-RPC batch
 * requests are rejected.</p>
 *
 * <p><code>initialize</code> and <code>ping</code> are always allowed.
 * <code>tools/list</code>, <code>tools/call</code>, and notifications are
 * enabled by default and can be disabled under <code>methods</code>. Every
 * other method is rejected.</p>
 *
 * <p>Tool rules are evaluated in declaration order and the first matching
 * rule wins. If no rule matches, the tool is allowed. Consequently, all tools
 * are allowed when <code>tools</code> is omitted. Add a final
 * <code>deny: ".*"</code> rule to change this into an allowlist.</p>
 *
 * @yaml
 * <pre><code>
 * - mcpProtection:
 *     methods:
 *       toolsList: true
 *       toolsCall: true
 *       notifications: true
 *     tools:
 *       - allow: "listProxies"
 *       - allow: "getStatistics"
 *       - allow: "getExchanges"
 *       - deny: ".*"           # tool names also support regular expressions
 * </code></pre>
 */
@MCElement(name = "mcpProtection")
public class MCPProtectionInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MCPProtectionInterceptor.class);

    private MCPProtectionMethods methods = new MCPProtectionMethods();
    private List<Rule> tools = List.of();
    private MCPProtectionValidator validator;

    public MCPProtectionInterceptor() {
        name = "MCP protection";
        setAppliedFlow(of(REQUEST));
    }

    @Override
    public void init() {
        super.init();
        validator = createValidator();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!exc.getRequest().isPOSTRequest()) {
            return reject(exc, new ValidationError(
                    null,
                    false,
                    405,
                    ERR_INVALID_REQUEST,
                    "HTTP method %s is not supported. Expected POST.".formatted(exc.getRequest().getMethod())
            ), true);
        }

        if (!exc.getRequest().isJSON()) {
            return reject(exc, new ValidationError(
                    null,
                    false,
                    415,
                    ERR_INVALID_REQUEST,
                    "Content-Type %s is not supported. Expected application/json.".formatted(
                            exc.getRequest().getHeader().getContentType()
                    )
            ), false);
        }

        return reject(
                exc,
                getValidator().validate(exc.getRequest().getBodyAsStringDecoded()),
                false
        );
    }

    /**
     * @description
     * <p>Configures the optional MCP method groups. <code>initialize</code>
     * and <code>ping</code> cannot be disabled by this interceptor.</p>
     */
    @MCChildElement(order = 1)
    public void setMethods(MCPProtectionMethods methods) {
        this.methods = methods == null ? new MCPProtectionMethods() : methods;
    }

    /**
     * @description
     * <p>Configures ordered allow and deny rules for tool names used by
     * <code>tools/call</code>. Rules support regular expressions. The first
     * matching rule wins; tools unmatched by any rule are allowed.</p>
     *
     * <p>When no rules are configured, all tools are allowed. To allow only
     * explicitly listed tools, place their <code>allow</code> rules first and
     * finish the list with <code>deny: ".*"</code>.</p>
     */
    @MCChildElement(order = 2)
    public void setTools(List<Rule> tools) {
        this.tools = tools == null ? List.of() : tools;
    }

    public MCPProtectionMethods getMethods() {
        return methods;
    }

    public List<Rule> getTools() {
        return tools;
    }

    private MCPProtectionValidator getValidator() {
        if (validator == null) {
            validator = createValidator();
        }
        return validator;
    }

    private MCPProtectionValidator createValidator() {
        return new MCPProtectionValidator(methods, tools);
    }

    private Outcome reject(Exchange exc, ValidationError error, boolean addAllowHeader) {
        if (error == null) {
            return CONTINUE;
        }

        log.info("Rejected MCP request: {}", error.message());
        if (error.notification()) {
            exc.setResponse(statusCode(error.httpStatus()).bodyEmpty().build());
            return RETURN;
        }
        exc.setResponse(createErrorResponse(error, addAllowHeader));
        return RETURN;
    }

    private Response createErrorResponse(ValidationError error, boolean addAllowHeader) {
        try {
            ResponseBuilder builder = statusCode(error.httpStatus())
                    .contentType(APPLICATION_JSON)
                    .body(error(error.responseId(), error.code(), error.message()).toJson());
            if (addAllowHeader) {
                builder.header("Allow", METHOD_POST);
            }
            return builder.build();
        } catch (IOException e) {
            throw new RuntimeException("Could not create MCP error response", e);
        }
    }
}
