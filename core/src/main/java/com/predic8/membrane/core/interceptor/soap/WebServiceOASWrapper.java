package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.serviceproxy.Rewrite;
import com.predic8.wsdl.Binding;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.Port;
import com.predic8.wsdl.Service;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.parameters.RequestBody;
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

    WebServiceOASWrapper(Definitions defs, Service serv) {
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
            PortMapping mapping = PortMapping.of(defs, serv, port, addresses);
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

        static PortMapping of(Definitions defs, Service svc, Port port, List<String> contexts) {
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
                    Components comp = new Components();
                    Paths paths = new Paths();
                    Map<String, RequestBody> rb = new HashMap<>();
                    Binding binding = port.getBinding();
                    binding.getPortType().getOperations().forEach(operation -> {
                        rb.put("RequestBodyName?", new RequestBody() {{
                            setContent(new Content() {{

                            }});
                        }});

                        paths.addPathItem("PathItemName?", new PathItem() {{
                            setPost(new Operation() {{

                            }});
                        }});
                    });
                    comp.setRequestBodies(rb);
                    setComponents(comp);
                    setPaths(paths);
                }});
            }
        }
}
