package com.predic8.membrane.core.openapi.model;

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestTest {

    @Test
    void doesSettingAJsonBodySetTheMimeType() {
        Request req =  Request.post().path("/boolean").body(new JsonBody(mapToJson(new HashMap<>())));
        assertEquals(APPLICATION_JSON,req.getMediaType().getBaseType());
    }
}