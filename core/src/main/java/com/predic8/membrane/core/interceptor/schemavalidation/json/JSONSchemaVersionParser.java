package com.predic8.membrane.core.interceptor.schemavalidation.json;

import com.networknt.schema.*;
import com.predic8.membrane.core.util.*;

import static com.networknt.schema.SchemaId.*;
import static com.networknt.schema.SchemaId.V4;
import static com.networknt.schema.SchemaId.V6;

public class JSONSchemaVersionParser {

    public static SpecVersion.VersionFlag parse(String version) {
        return SpecVersion.VersionFlag.fromId(aliasToSpecId(version)).get();
    }

    static String aliasToSpecId(String alias) {
        return switch (alias) {
            case "04","draft-04" -> V4;
            case "06","draft-06" -> V6;
            case "07","draft-07" -> V7;
            case "2019-09","draft-2019-09" -> V201909;
            case "2020-12", "draft-2020-12" -> V202012;
            default -> throw new ConfigurationException("Unknown JSON Schema version: " + alias);
        };
    }
}
