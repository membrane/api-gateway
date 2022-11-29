package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.openapi.validators.*;

import java.util.*;

public class ValidationStatsKey {

    public String method;
    public String path;
    public String uriTemplate;
    public String schemaType;
    public String complexType;
    public String validatedEntityType;
    public String validatedEntity;
    public String jsonpointer;

    ValidationStatsKey(ValidationContext vc) {
        method = vc.getMethod();
        path = vc.getPath();
        uriTemplate = vc.getUriTemplate();
        schemaType = vc.getSchemaType();
        complexType = vc.getComplexType();
        validatedEntityType = vc.getValidatedEntityType().name();
        validatedEntity = vc.getValidatedEntity();
        jsonpointer = vc.getJSONpointer();
    }

    public Map<String,String> getLabels() {
        Map<String,String> m = new HashMap<>();
        m.put("method",method);
        m.put("path",path);
        m.put("uritemplate",uriTemplate);
        m.put("schematype",schemaType);
        m.put("complextype",complexType);
        m.put("entitytype",validatedEntityType);
        m.put("entity",validatedEntity);
        m.put("jsonpointer",jsonpointer);
        return m;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ValidationStatsKey that = (ValidationStatsKey) o;

        if (method != null ? !method.equals(that.method) : that.method != null) return false;
        if (path != null ? !path.equals(that.path) : that.path != null) return false;
        if (uriTemplate != null ? !uriTemplate.equals(that.uriTemplate) : that.uriTemplate != null) return false;
        if (schemaType != null ? !schemaType.equals(that.schemaType) : that.schemaType != null) return false;
        if (complexType != null ? !complexType.equals(that.complexType) : that.complexType != null) return false;
        if (!validatedEntityType.equals(that.validatedEntityType)) return false;
        if (validatedEntity != null ? !validatedEntity.equals(that.validatedEntity) : that.validatedEntity != null)
            return false;
        return jsonpointer != null ? jsonpointer.equals(that.jsonpointer) : that.jsonpointer == null;
    }

    @Override
    public int hashCode() {
        int result = method != null ? method.hashCode() : 0;
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (uriTemplate != null ? uriTemplate.hashCode() : 0);
        result = 31 * result + (schemaType != null ? schemaType.hashCode() : 0);
        result = 31 * result + (complexType != null ? complexType.hashCode() : 0);
        result = 31 * result + validatedEntityType.hashCode();
        result = 31 * result + (validatedEntity != null ? validatedEntity.hashCode() : 0);
        result = 31 * result + (jsonpointer != null ? jsonpointer.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ValidationStatsKey{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", uriTemplate='" + uriTemplate + '\'' +
                ", schemaType='" + schemaType + '\'' +
                ", complexType='" + complexType + '\'' +
                ", validatedEntityType='" + validatedEntityType + '\'' +
                ", validatedEntity='" + validatedEntity + '\'' +
                ", jsonpointer='" + jsonpointer + '\'' +
                '}';
    }
}
