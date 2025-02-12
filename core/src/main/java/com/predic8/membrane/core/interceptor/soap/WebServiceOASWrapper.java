package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.serviceproxy.Rewrite;
import com.predic8.wsdl.Port;
import com.predic8.wsdl.Service;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.getIdFromAPI;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

class WebServiceOASWrapper {

    // All ports aggregated and mapped by unique context url; will be equal to services to the user.
    private final Map<String, PortMapping> services;

    WebServiceOASWrapper(Service serv) {
        services = new HashMap<>();
        var aggregatedPorts = serv.getPorts().stream()
            .collect(groupingBy(Port::getBinding))
            .entrySet().stream()
            .collect(toMap(
        entry -> entry.getValue().getFirst(),
        entry -> entry.getValue().stream()
                .map(p -> p.getAddress().getLocation())
                .toList()
        ));

        aggregatedPorts.forEach((port, addresses) -> {
            PortMapping mapping = PortMapping.of(serv, port, addresses);
            mapping.api.getServers().forEach(server ->
                services.put(server.getUrl(), mapping)
            );
        });
    }

    PortMapping getMapping(String context) {
        return services.get(context);
    }

    Stream<Entry<String, OpenAPIRecord>> getApiRecords() {
        return services.values().stream().collect(toMap(
                e -> getIdFromAPI(e.api),
                e -> new OpenAPIRecord(e.api, new OpenAPISpec() {{
                    setRewrite(new Rewrite());
                }})
        )).entrySet().stream();
    }

    record PortMapping(Port port, OpenAPI api) {

        static PortMapping of(Service svc, Port port, List<String> contexts) {
                return new PortMapping(port, new OpenAPI() {{
                    setInfo(new Info() {{
                            setTitle("%s-%s".formatted(svc.getName(), port.getName()));
                            setDescription("Service %s provided as API through Membrane API Gateway.\n%s".formatted(svc.getName(), getDocumentationOrEmpty()));
                            setVersion("1.0.0");
                        }

                        private String getDocumentationOrEmpty() {
                            if (port.getDocumentation() != null) {
                                return port.getDocumentation().getContent();
                            }
                            return "";
                        }
                    });
                    setServers(contexts.stream().map(server ->
                            (Server) new Server() {{
                                setUrl(server);
                            }}
                    ).toList());
                    setComponents(new Components() {{

                    }});
                    setPaths(new Paths() {{

                    }});
                }});
            }
        }
}
