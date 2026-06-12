package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.mcp.MCPProtectionValidator.ValidationError;
import com.predic8.membrane.core.jsonrpc.JSONRPCResponse;
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
 * <p>Protects an MCP endpoint by validating the JSON-RPC request structure and
 * controlling access to optional MCP methods and tool names.</p>
 *
 * <p><code>initialize</code> and <code>ping</code> are always allowed.
 * <code>tools/list</code>, <code>tools/call</code>, and notifications can be
 * disabled under <code>methods</code>. All other MCP methods are
 * rejected.</p>
 *
 * @yaml
 * <pre><code>
 * - mcpProtection:
 *     methods:
 *       toolsList: true
 *       toolsCall: false
 *       notifications: true
 *     tools:
 *       - allow: "^(listProxies|getStatistics|getExchanges)$"
 *       - deny: ".*"
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
                    405,
                    ERR_INVALID_REQUEST,
                    "HTTP method %s is not supported. Expected POST.".formatted(exc.getRequest().getMethod())
            ), true);
        }

        if (!exc.getRequest().isJSON()) {
            return reject(exc, new ValidationError(
                    null,
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

    @MCChildElement(order = 1)
    public void setMethods(MCPProtectionMethods methods) {
        this.methods = methods == null ? new MCPProtectionMethods() : methods;
    }

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
