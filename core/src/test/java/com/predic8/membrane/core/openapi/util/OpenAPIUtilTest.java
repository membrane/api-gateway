package com.predic8.membrane.core.openapi.util;

import com.predic8.membrane.core.util.*;
import io.swagger.parser.*;
import io.swagger.v3.oas.models.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.Assert.*;

public class OpenAPIUtilTest {

    @Test
    public void getIdFromAPITest() {
        assertEquals("customers-api-v1-0", getIdFromAPI(getApi("/openapi/specs/customers.yml")) );
        assertEquals("servers-3-api-v1-0", getIdFromAPI(getApi("/openapi/specs/info-3-servers.yml")) );
    }

    private OpenAPI getApi(String pfad) {
        return new OpenAPIParser().readContents(FileUtil.readInputStream(getResourceAsStream(this,pfad)), null, null).getOpenAPI();
    }

}