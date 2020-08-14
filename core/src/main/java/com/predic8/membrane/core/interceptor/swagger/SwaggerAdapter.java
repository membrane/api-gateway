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

import io.swagger.models.Path;
import io.swagger.models.RefPath;
import io.swagger.models.Swagger;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.parser.converter.SwaggerConverter;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SwaggerAdapter implements SwaggerCompatibleOpenAPI {
    final Swagger api;

    public SwaggerAdapter(Swagger api) {
        this.api = api;
    }

    @Override
    public String getHost() {

        return api.getHost();
    }

    @Override
    public String getBasePath() {
        return api.getBasePath();
    }

    @Override
    public Paths getPaths() {
        Paths v3Paths = new Paths();
        Map<String, Path> pathMap = Optional.ofNullable(api.getPaths()).orElse(new HashMap<>());
        for (String pathname : pathMap.keySet()) {
            io.swagger.models.Path v2Path = api.getPath(pathname);
            try {
                SwaggerConverter swaggerConverter = new SwaggerConverter();
                PathItem v3Path = swaggerConverter.convert(v2Path);
                v3Paths.put(pathname, v3Path);
            } catch (Exception e) {
                // conversion apparently not necessary

            }
        }
        return v3Paths;
    }

    public PathItem convert(Path v2Path) {

        PathItem v3Path = new PathItem();

        if (v2Path instanceof RefPath) {

            v3Path.set$ref(((RefPath) v2Path).get$ref());
        } else {
            // Apparently no conversion needed
        }
        return  v3Path;
    }



    @Override
    public byte[] toJSON() throws UnsupportedEncodingException {
        return Json.pretty(api).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void setHost(String newHost) {
        api.setHost(newHost);
    }

    @Override
    public boolean isNull() {
        return (api == null);
    }
}
