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
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIProxy.*;

public class OpenAPIAPIInterceptor extends AbstractInterceptor {

    Collection<OpenAPIRecord> apis;
    private final ObjectMapper om = new ObjectMapper();

    public OpenAPIAPIInterceptor(Collection<OpenAPIRecord> apis) {
        this.apis = apis;
        setFlow(Flow.Set.REQUEST);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (!exc.getRequest().getUri().startsWith("/openapi/"))
            return CONTINUE;

        Map<String,Object> apiDesc = new HashMap<>();
        for (OpenAPIRecord record: apis) {
            Map<String,Object > details = new HashMap<>();

            details.put("version", record.api.getInfo().getVersion());
            details.put("validation", computeValidationMap(record.api));

            ArrayList<Map <String,String>> servers = new ArrayList<>();
            for (Server oaServer: record.api.getServers()) {
                Map<String,String>  server = new HashMap<>();
                server.put("url",oaServer.getUrl());
                if (oaServer.getDescription() != null)
                    server.put("description", oaServer.getDescription());
                servers.add(server);
            }


            details.put("servers", servers);
            apiDesc.put(record.api.getInfo().getTitle(),details);
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
        return "You can access the live information at:<br/><br/>/openapi/";
    }


    @SuppressWarnings("unchecked")
    private Map<String,Boolean> computeValidationMap(OpenAPI api) {
        Map<String,Boolean> validationMap = new HashMap<>();
        validationMap.put("requests",false);
        validationMap.put("responses",false);
        if (api.getExtensions() != null) {
            validationMap = (Map<String,Boolean>) api.getExtensions().get(X_MEMBRANE_VALIDATION);
        }
        return validationMap;
    }
}
