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
            try {
                PortMapping mapping = PortMapping.of(serv, port, addresses);
                mapping.api.getServers().forEach(server ->
                        services.put(server.getUrl(), mapping)
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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

        static PortMapping of(Service svc, Port port, List<String> contexts) throws Exception {
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
                    Map<String, ApiResponse> res = new HashMap<>();

                    Xsd2OasSchema x2o = new Xsd2OasSchema();
                    Binding binding = port.getBinding();
                    binding.getPortType().getOperations().forEach(operation -> {
                        String opName = operation.getName();
                        String requestBodyName = opName + "RequestBody";
                        String responseName = opName + "Response";

                        Schema xmlSchema;
                        try {
                            xmlSchema = x2o.convert(
                                    toInputStream(operation.getInput().getMessage().getParts().getFirst().getElement().getAsString(), UTF_8)
                            );
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        rb.put(requestBodyName, new RequestBody() {{
                            setContent(new Content() {{
                                addMediaType("application/xml", new MediaType() {{
                                    setSchema(xmlSchema);
                                }});
                                addMediaType("application/json", new MediaType() {{
                                    setSchema(xmlSchema);
                                }});
                            }});
                            setDescription("Request for operation " + opName);
                            setRequired(true);
                        }});

                        res.put(responseName, new ApiResponse() {{
                            setDescription("Successful response from " + opName);
                            if (operation.getOutput() != null && operation.getOutput().getMessage() != null) {
                                String outputXsd = operation.getOutput().getMessage().getParts().get(0).getElement().getAsString();
                                try {
                                    Schema<?> outputXmlSchema = x2o.convert(toInputStream(outputXsd, UTF_8));
                                    setContent(new Content() {{
                                        addMediaType("application/xml", new MediaType() {{
                                            setSchema(outputXmlSchema);
                                        }});
                                        addMediaType("application/json", new MediaType() {{
                                            setSchema(outputXmlSchema);
                                        }});
                                    }});
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }});

                        paths.addPathItem("/" + opName.toLowerCase(), new PathItem() {{
                            setPost(new Operation() {{
                                setOperationId(opName);
                                setSummary("Invoke operation " + opName);
                                setRequestBody(new RequestBody() {{
                                    $ref("#/components/requestBodies/" + requestBodyName);
                                }});
                                setResponses(new ApiResponses() {{
                                    addApiResponse("200", new ApiResponse() {{
                                        $ref("#/components/responses/" + responseName);
                                    }});
                                }});
                            }});
                        }});
                    });

                    comp.setRequestBodies(rb);
                    comp.setResponses(res);
                    setComponents(comp);
                    setPaths(paths);
                }});
            }
        }
}
