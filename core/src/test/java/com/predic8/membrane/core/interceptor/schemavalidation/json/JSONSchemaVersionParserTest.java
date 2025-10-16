package com.predic8.membrane.core.interceptor.schemavalidation.json;

import com.networknt.schema.*;
import org.junit.jupiter.api.*;

import static com.networknt.schema.SpecVersion.VersionFlag.*;
import static com.predic8.membrane.core.interceptor.schemavalidation.json.JSONSchemaVersionParser.parse;
import static org.junit.jupiter.api.Assertions.*;

class JSONSchemaVersionParserTest {

    @Test
    void parseFromAlias() {
        assertEquals(V4, parse("04"));
        assertEquals(V6, parse("06"));
        assertEquals(V7, parse("07"));
        assertEquals(V4, parse("draft-04"));
        assertEquals(V6, parse("draft-06"));
        assertEquals(V7, parse("draft-07"));
        assertEquals(V201909, parse("2019-09"));
        assertEquals(V202012, parse("2020-12"));
    }
}