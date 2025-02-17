/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.serviceproxy.Rewrite;
import com.predic8.wsdl.Binding;
import com.predic8.wsdl.Port;
import com.predic8.wsdl.Service;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.getIdFromAPI;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.io.IOUtils.toInputStream;

class WebServiceOASWrapper {

    // Aggregated ports mapped by unique context url.
    private final Map<String, PortMapping> services;

    WebServiceOASWrapper(Service svc) {
        services = new HashMap<>();
        getAggregatedPorts(svc).forEach((port, addresses) -> {
            try {
                PortMapping mapping = PortMapping.of(svc, port, addresses);
                mapping.getApi().getServers().forEach(server ->
                        services.put(server.getUrl(), mapping)
                );
            } catch (Exception e) {
                // TODO ConfigurationException?
                throw new RuntimeException(e);
            }
        });
    }

    static @NotNull Map<Port, @NotNull List<String>> getAggregatedPorts(Service svc) {
        return svc.getPorts().stream()
                .collect(groupingBy(Port::getBinding))
                .entrySet().stream()
                .collect(toMap(
            entry -> entry.getValue().getFirst(),
            entry -> entry.getValue().stream()
                    .map(p -> p.getAddress().getLocation())
                    .toList()
                ));
    }

    PortMapping getMapping(String context) {
        return services.get(context);
    }

    Stream<Entry<String, OpenAPIRecord>> getApiRecords() {
        return services.values().stream().collect(toMap(
                e -> getIdFromAPI(e.getApi()),
                e -> new OpenAPIRecord(e.getApi(), new OpenAPISpec() {{
                    setRewrite(new Rewrite());
                }})
        )).entrySet().stream();
    }
}