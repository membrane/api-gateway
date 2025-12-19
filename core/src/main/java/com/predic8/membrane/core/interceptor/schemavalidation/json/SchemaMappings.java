package com.predic8.membrane.core.interceptor.schemavalidation.json;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@MCElement(name = "schemaMappings")
public class SchemaMappings {

    private List<Schema> schemas = new ArrayList<>();

    public Map<String, String> getSchemaMap() {
        Map<String, String > referenceSchemas = new HashMap<>();
        schemas.forEach(schema ->  referenceSchemas.put(schema.getId(), schema.getLocation()));
        return referenceSchemas;
    }

    @Required
    @MCChildElement
    public void setSchemas(List<Schema> schemas) {
        this.schemas = schemas;
    }

    public List<Schema> getSchemas() {
        return schemas;
    }

    @MCElement(name = "schema")
    public static class Schema {
        private String id;

        private String location;

        @MCAttribute
        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @MCAttribute
        public void setLocation(String location) {
            this.location = location;
        }

        public String getLocation() {
            return location;
        }
    }
}
