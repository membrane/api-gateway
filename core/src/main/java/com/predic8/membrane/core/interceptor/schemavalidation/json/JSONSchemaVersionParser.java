package com.predic8.membrane.core.interceptor.schemavalidation.json;

import com.networknt.schema.*;
import com.predic8.membrane.core.util.*;

import static com.networknt.schema.SchemaId.*;
import static com.networknt.schema.SchemaId.V4;
import static com.networknt.schema.SchemaId.V6;

public class JSONSchemaVersionParser {

    /**
     * Resolve a JSON Schema version alias to its corresponding SpecVersion.VersionFlag.
     *
     * @param version a version alias such as "04", "draft-04", "07", "draft-2019-09" or "2020-12"
     * @return the SpecVersion.VersionFlag that corresponds to the given alias
     * @throws com.predic8.membrane.core.util.ConfigurationException if the alias is not a recognized JSON Schema version
     */
    public static SpecVersion.VersionFlag parse(String version) {
        return SpecVersion.VersionFlag.fromId(aliasToSpecId(version)).get();
    }

    /**
     * Resolve a JSON Schema version alias to the internal SchemaId string.
     *
     * <p>Recognized aliases include short numeric forms (e.g. "04", "06", "07"),
     * draft-prefixed forms (e.g. "draft-04", "draft-07") and calendar-year forms
     * (e.g. "2019-09", "2020-12" or "draft-2019-09", "draft-2020-12").</p>
     *
     * @param alias the user-facing version alias to resolve (e.g. "draft-07", "2019-09")
     * @return the corresponding internal SchemaId string (one of V4, V6, V7, V201909, V202012)
     * @throws ConfigurationException if the alias is not one of the recognized versions
     */
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