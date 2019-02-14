package com.predic8.membrane.core.interceptor.swagger;

import com.google.common.collect.Lists;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.servers.Server;

import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenAPIAdapter implements SwaggerCompatibleOpenAPI {
    final OpenAPI api;

    public OpenAPIAdapter(OpenAPI api) {
        this.api = api;
    }

    @Override
    public String getHost() {

        Optional<Server> server = null;
        server = api.getServers().stream().findFirst();
        if (!server.isPresent()) throw new RuntimeException("server not set");
        Matcher hostMatcher = Pattern.compile("://(.*?)/").matcher(server.get().getUrl());
        if (hostMatcher.find()) {
            return hostMatcher.group(1);
        } else {
            //throw new RuntimeException("not implemented"); // can relative paths appear here?
            return "";
        }
    }

    @Override
    public String getBasePath() {
        Optional<Server> server = api.getServers().stream().findFirst();
        if (!server.isPresent()) throw new RuntimeException("server not set");
        // match whole URL after stripping schema & hostname
        Matcher hostMatcher = Pattern.compile("(.*?://(.*?))?(/.*)").matcher(server.get().getUrl());
        if (hostMatcher.find()) {
            return hostMatcher.group(3);
        } else {
            throw new RuntimeException("not implemented");
        }
    }

    @Override
    public Paths getPaths() {
        return api.getPaths();
    }

    @Override
    public byte[] toJSON() throws UnsupportedEncodingException {
        return Json.pretty(api).getBytes("UTF-8");
    }

    @Override
    public void setHost(String newHost) {
        Server server = new Server();
        server.setUrl("http://" + newHost);
        api.setServers(Lists.newArrayList(server));
        // TODO - überhaupt nötig?
        //api.setHost()
    }

    @Override
    public boolean isNull() {
        return (api == null);
    }
}
