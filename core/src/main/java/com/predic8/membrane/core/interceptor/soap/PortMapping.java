package com.predic8.membrane.core.interceptor.soap;

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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;

record PortMapping(Port port, OpenAPI api) {

    static PortMapping of(Service svc, Port port, List<String> contexts) throws Exception {
        return new PortMapping(port, new OpenAPI() {{
            setInfo(new Info() {{
                setTitle("%s-%s".formatted(svc.getName(), port.getName()));
                setDescription("Service %s provided as API through Membrane API Gateway.\n%s"
                        .formatted(svc.getName(), getDocumentationOrEmpty(port)));
                setVersion("1.0.0");
            }});

            setServers(contexts.stream().map(url -> (Server) new Server() {{ setUrl(url); }}).toList());

            Components comp = new Components();
            Paths paths = new Paths();
            Map<String, RequestBody> requestBodies = new HashMap<>();
            Map<String, ApiResponse> responses = new HashMap<>();

            Xsd2OasSchema xsd2oas = new Xsd2OasSchema();
            Binding binding = port.getBinding();
            binding.getPortType().getOperations().forEach(operation -> {
                String opName = operation.getName();
                String requestBodyName = opName + "RequestBody";
                String responseName = opName + "Response";

                Schema requestSchema;
                try {
                    requestSchema = xsd2oas.convert(toInputStream(
                            operation.getInput().getMessage().getParts().getFirst().getElement().getAsString(), UTF_8)
                    );
                } catch (Exception e) {
                    // TODO ConfigurationException?
                    throw new RuntimeException(e);
                }
                requestBodies.put(requestBodyName, buildRequestBody(opName, requestSchema));

                Schema<?> outputSchema;
                try {
                    outputSchema = xsd2oas.convert(toInputStream(
                            operation.getOutput().getMessage().getParts().getFirst().getElement().getAsString(), UTF_8)
                    );
                } catch (Exception e) {
                    // TODO ConfigurationException?
                    throw new RuntimeException(e);
                }
                responses.put(responseName, buildApiResponse(opName, outputSchema));

                paths.addPathItem("/" + opName, new PathItem() {{
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

            comp.setRequestBodies(requestBodies);
            comp.setResponses(responses);
            setComponents(comp);
            setPaths(paths);
        }});
    }

    private static RequestBody buildRequestBody(String opName, Schema schema) {
        var mediaType = new MediaType();
        mediaType.setSchema(schema);

        return new RequestBody() {{
            setContent(new Content() {{
                addMediaType("application/xml", mediaType);
                addMediaType("application/json", mediaType);
            }});
            setDescription("Request for operation " + opName);
            setRequired(true);
        }};
    }

    private static ApiResponse buildApiResponse(String opName, Schema<?> outputSchema) {
        return new ApiResponse() {{
            setDescription("Successful response from " + opName);
            if (outputSchema != null) {
                var mediaType = new MediaType();
                mediaType.setSchema(outputSchema);

                setContent(new Content() {{
                    addMediaType("application/xml", mediaType);
                    addMediaType("application/json", mediaType);
                }});
            }
        }};
    }

    private static String getDocumentationOrEmpty(Port port) {
        if (port.getDocumentation() != null) {
            return port.getDocumentation().getContent();
        }
        return "";
    }

    public Port getPort() {
        return port;
    }

    public OpenAPI getApi() {
        return api;
    }
}