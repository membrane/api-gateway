/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
    }

    @Override
    public boolean isNull() {
        return (api == null);
    }
}
