package com.predic8.membrane.core.interceptor.soap;

import com.predic8.wsdl.Port;
import com.predic8.wsdl.PortType;
import com.predic8.wsdl.Service;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

/**
 * Does currently not support multiple ports - will always default to the first one.
 * Maybe if only one port, default to service name, if multiple, create API definition
 * per port and use compound services names like "CityService-CityPort".
 */
class WebServiceOASWrapper {

    // All ports aggregated and mapped by unique context url; will be equal to services to the user.
    private final Map<String, PortMapping> services;

    public WebServiceOASWrapper(Service serv) {
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
            PortMapping mapping = PortMapping.of(serv.getName(), port, addresses);
            mapping.api.getServers().forEach(server ->
                services.put(server.getUrl(), mapping)
            );
        });
    }

    public PortMapping getMapping(String context) {
        return services.get(context);
    }

    static class PortMapping {

        private final Port port;
        private final OpenAPI api;

        PortMapping(Port port, OpenAPI api) {
            this.port = port;
            this.api = api;
        }

        static PortMapping of(String svcName, Port port, List<String> contexts) {
            return new PortMapping(port, new OpenAPI() {{
                setInfo(new Info() {{
                    setTitle(svcName);
                    setDescription("Service %s provided as API through Membrane API Gateway.\n%s".formatted(svc.getName(), svc.getDocumentation().getContent()));
                    setVersion("1.0.0");
                }});
                setComponents(new Components() {{

                }});
            }});
        }

        public OpenAPI getApi() {
            return api;
        }

        public Port getPort() {
            return port;
        }
    }
}
