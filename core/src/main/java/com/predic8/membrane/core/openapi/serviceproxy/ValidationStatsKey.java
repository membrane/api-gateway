/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.core.openapi.validators.*;

import java.util.*;

public class ValidationStatsKey {

    private final String method;
    private final String path;
    private final String uriTemplate;
    private final String schemaType;
    private final String complexType;
    private final String validatedEntityType;
    private final String validatedEntity;
    private final String jsonpointer;
    private final Map<String, String> labels;

    ValidationStatsKey(ValidationContext vc) {
        method = vc.getMethod();
        path = vc.getPath();
        uriTemplate = valueOrEmptyString(vc.getUriTemplate());
        schemaType = valueOrEmptyString(vc.getSchemaType());
        complexType = valueOrEmptyString(vc.getComplexType());
        validatedEntityType = vc.getValidatedEntityType().name();
        validatedEntity = vc.getValidatedEntity();
        jsonpointer = vc.getJSONpointer();
        labels = new ImmutableMap.Builder<String, String>()
            .put("method", method)
            .put("path", path)
            .put("uritemplate", uriTemplate)
            .put("schematype", schemaType)
            .put("complextype", complexType)
            .put("entitytype", validatedEntityType)
            .put("entity", validatedEntity)
            .put("jsonpointer", jsonpointer)
            .build();
    }

    private static String valueOrEmptyString(String v) {
        return v == null ? "" : v;
    }


    public Map<String,String> getLabels() {
        return labels;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ValidationStatsKey that = (ValidationStatsKey) o;

        if (!Objects.equals(method, that.method)) return false;
        if (!Objects.equals(path, that.path)) return false;
        if (!Objects.equals(uriTemplate, that.uriTemplate)) return false;
        if (!Objects.equals(schemaType, that.schemaType)) return false;
        if (!Objects.equals(complexType, that.complexType)) return false;
        if (!Objects.equals(validatedEntityType, that.validatedEntityType)) return false;
        if (!Objects.equals(validatedEntity, that.validatedEntity))
            return false;
        return Objects.equals(jsonpointer, that.jsonpointer);
    }

    @Override
    public int hashCode() {
        int result = method != null ? method.hashCode() : 0;
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (uriTemplate != null ? uriTemplate.hashCode() : 0);
        result = 31 * result + (schemaType != null ? schemaType.hashCode() : 0);
        result = 31 * result + (complexType != null ? complexType.hashCode() : 0);
        result = 31 * result + (validatedEntityType != null ? validatedEntityType.hashCode() : 0);
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
