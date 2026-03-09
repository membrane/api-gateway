package com.predic8.membrane.core.util.soap;

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import com.predic8.membrane.core.util.wsdl.parser.schema.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.util.wsdl.parser.Binding.Style.*;
import static org.junit.jupiter.api.Assertions.*;

class WSDLParserTest {

    @Test
    void simpleSchema() throws Exception {
        var definitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        assertEquals(1, definitions.getSchemas().size());
        var schema = definitions.getSchemas().getFirst();
        assertEquals("https://predic8.de/cities", schema.getTargetNamespace());
        var schemaElements = schema.getSchemaElements();
        assertEquals(2, schemaElements.size());
        var schemaElementNames = schemaElements.stream().map(SchemaElement::getName).toList();
        assertEquals(List.of("getCity", "getCityResponse"), schemaElementNames);

        var services = definitions.getServices();
        assertEquals(1, services.size());
        var service = services.getFirst();
        assertEquals("CityService", service.getName());
        var ports = service.getPorts();
        assertEquals(1, ports.size());
        var port = ports.getFirst();
        assertEquals("CityPort", port.getName());
        var address = port.getAddress();
        assertEquals("http://localhost:2001/services/cities", address.getLocation());
        var binding = port.getBinding();
        assertEquals("CitySoapBinding", binding.getName());
        assertEquals(DOCUMENT, binding.getStyle());
        var portType = binding.getPortType();
        assertEquals("CityPort", portType.getName());
        var operations = portType.getOperations();
        assertEquals(1, operations.size());
        var getCityOperation = operations.getFirst();
        var inputs = getCityOperation.getInputs();
        assertEquals(1, inputs.size());
        var getCityInput = inputs.getFirst();
        var getCityPart = getCityInput.getPart();
        assertEquals("getCity", getCityPart.getName());
        assertEquals("getCity", getCityPart.getElementQName().getLocalPart());
        assertEquals("https://predic8.de/cities", getCityPart.getElementQName().getNamespaceURI());

        assertEquals(1,definitions.getBindings().size());
        var binding1 = definitions.getBindings().getFirst();
        assertEquals("CitySoapBinding", binding1.getName());
        assertEquals(DOCUMENT, binding1.getStyle());

        assertEquals(2,definitions.getMessages().size());
        assertEquals("City", definitions.getMessages().getFirst().getName());
        assertEquals("CityResponse", definitions.getMessages().getLast().getName());
    }

    @Test
    void includeImport() throws Exception {
        var definitions = Definitions.parse(new ResolverMap(), "classpath://ws/include/include.wsdl");
        assertEquals(1, definitions.getSchemas().size());
        var embedded = definitions.getSchemas().getFirst();
        assertEquals("http://example.com/test", embedded.getTargetNamespace());
        var schemaElements = embedded.getSchemaElements();
        assertEquals(3, schemaElements.size());
        var schemaElementNames = schemaElements.stream().map(SchemaElement::getName).toList();
        assertEquals(List.of("test", "testResponse", "du"), schemaElementNames);

        var includeMessageStructureTypes = embedded.getIncludes().getFirst();
        assertEquals("xsd/inc/MessageStructureTypes2.xsd", includeMessageStructureTypes.getSchemaLocation());

        assertEquals(1, embedded.getImports().size());
        var imported = embedded.getImports().getFirst();
        assertEquals("http://example.com/test/data-types", imported.getNamespace());
        var importedSchemaElements = imported.getSchema().getSchemaElements();
        assertEquals(2, importedSchemaElements.size());
        var importedSchemaElementNames = importedSchemaElements.stream().map(SchemaElement::getName).toList();
        assertEquals(List.of("B5", "B6"), importedSchemaElementNames);

    }

    @Test
    void abstractWSDL() throws Exception {
        assertEquals(1, Definitions.parse(new ResolverMap(), "classpath:/ws/abstract-service-no-binding.wsdl").getPortTypes().size());
    }

}