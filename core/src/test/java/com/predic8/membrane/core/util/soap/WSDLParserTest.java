package com.predic8.membrane.core.util.soap;

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import com.predic8.membrane.core.util.wsdl.parser.schema.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.util.wsdl.parser.Binding.Style.DOCUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WSDLParserTest {

    @Test
    void simpleSchema() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/ws/cities.wsdl")) {
            var definitions = new Definitions(new WSDLParserContext(null, null, null));
            definitions.parse(is, null);
            assertEquals(1,definitions.getSchemas().size());
            var schema = definitions.getSchemas().getFirst();
            assertEquals("https://predic8.de/cities", schema.getTargetNamespace());
            var schemaElements = schema.getSchemaElements();
            assertEquals(2, schemaElements.size());
            var schemaElementNames = schemaElements.stream().map(SchemaElement::getName).toList();
            assertEquals(List.of("getCity","getCityResponse"), schemaElementNames);

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
            assertEquals("CityPort",portType.getName());
            var operations = portType.getOperations();
            assertEquals(1,operations.size());
            var getCityOperation = operations.getFirst();
            var inputs = getCityOperation.getInputs();
            assertEquals(1, inputs.size());
            var getCityInput = inputs.getFirst();
            var getCityPart = getCityInput.getPart();
            assertEquals("getCity", getCityPart.getName());
            assertEquals("getCity", getCityPart.getElementQName().getLocalPart());
            assertEquals("https://predic8.de/cities", getCityPart.getElementQName().getNamespaceURI());
        }
    }

     @Test
    void includeImport() throws Exception {
         var resolver = new ResolverMap();
         String uri = "classpath://ws/include/include.wsdl";
         try (InputStream is =  resolver.resolve(uri)) {
             var ctx = new WSDLParserContext(null,resolver,uri);
             var definitions = new Definitions(ctx);
             definitions.parse(is,resolver);
             assertEquals(1, definitions.getSchemas().size());
             var schema = definitions.getSchemas().getFirst();
             assertEquals("http://example.com/test", schema.getTargetNamespace());
             var schemaElements = schema.getSchemaElements();
             assertEquals(3, schemaElements.size());
             var schemaElementNames = schemaElements.stream().map(SchemaElement::getName).toList();
             assertEquals(List.of("test", "testResponse","du"), schemaElementNames);

             var includeMessageStructureTypes = schema.getIncludes().getFirst();
             assertEquals("xsd/inc/MessageStructureTypes2.xsd", includeMessageStructureTypes.getSchemaLocation());

             assertEquals(1, definitions.getImportedSchemas().size());
             var importedSchema = definitions.getImportedSchemas().getFirst();
             assertEquals("http://example.com/test/data-types", importedSchema.getTargetNamespace());
             var importedSchemaElements = importedSchema.getSchemaElements();
             assertEquals(2, importedSchemaElements.size());
             var importedSchemaElementNames = importedSchemaElements.stream().map(SchemaElement::getName).toList();
             assertEquals(List.of("B5","B6"), importedSchemaElementNames);
         }
     }

     @Test
     void abstractWSDL() throws IOException {
        try (var is = getClass().getResourceAsStream("/ws/abstract-service-no-binding.wsdl")) {
            var definitions = new Definitions(new WSDLParserContext(null, null, null));
            definitions.parse(is, null);
            assertEquals(1, definitions.getPortTypes().size());
        }
     }

}