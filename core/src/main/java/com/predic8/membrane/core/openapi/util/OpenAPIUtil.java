package com.predic8.membrane.core.openapi.util;

import io.swagger.v3.oas.models.*;

import static com.predic8.membrane.core.openapi.util.Utils.normalizeForId;

public class OpenAPIUtil {

    public static String getIdFromAPI(OpenAPI api) {
        return normalizeForId(api.getInfo().getTitle() + "-v" + api.getInfo().getVersion());
    }
}
