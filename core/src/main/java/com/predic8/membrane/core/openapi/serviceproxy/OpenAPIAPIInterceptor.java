package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.servers.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

public class OpenAPIAPIInterceptor extends AbstractInterceptor {

    List<OpenAPI> apis;
    private final ObjectMapper om = new ObjectMapper();

    public OpenAPIAPIInterceptor(List<OpenAPI> apis) {
        this.apis = apis;
        setFlow(Flow.Set.REQUEST);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (!exc.getRequest().getUri().startsWith("/openapi/"))
            return CONTINUE;

        Map apiDesc = new HashMap();
        for (OpenAPI api: apis) {
            Map details = new HashMap();

            details.put("version", api.getInfo().getVersion());
            details.put("validation", computeValidationMap(api));

            ArrayList servers = new ArrayList<>();
            for (Server oaServer: api.getServers()) {
                Map server = new HashMap();
                server.put("url",oaServer.getUrl());
                if (oaServer.getDescription() != null)
                    server.put("description", oaServer.getDescription());
                servers.add(server);
            }


            details.put("servers", servers);
            apiDesc.put(api.getInfo().getTitle(),details);
        }

        exc.setResponse(Response.ok().contentType("application/json").body(om.writerWithDefaultPrettyPrinter().writeValueAsBytes(apiDesc)).build());

        return RETURN;
    }

    @Override
    public String getDisplayName() {
        return "OpenAPI API";
    }

    @Override
    public String getShortDescription() {
        return "Access live information about the configuration.";
    }

    @Override
    public String getLongDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("You can access the live information at:<br/><br/>/openapi/");
        return sb.toString();
    }


    private Map computeValidationMap(OpenAPI api) {
        Map validationMap = new HashMap();
        validationMap.put("requests",false);
        validationMap.put("responses",false);
        if (api.getExtensions() != null) {
            validationMap = (Map) api.getExtensions().get("x-validation");
        }
        return validationMap;
    }
}
