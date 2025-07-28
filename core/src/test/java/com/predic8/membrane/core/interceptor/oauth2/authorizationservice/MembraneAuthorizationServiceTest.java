package com.predic8.membrane.core.interceptor.oauth2.authorizationservice;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService.*;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

class MembraneAuthorizationServiceTest {

    MembraneAuthorizationService as;

    private final String CONFIG = """
             {
                "issuer": "https://login.microsoftonline.com/{tenant}/v2.0/",
                    "authorization_endpoint": "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize?p={policy}",
                    "token_endpoint":        "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token?p={policy}",
                    "end_session_endpoint":  "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/logout?p={policy}",
                    "jwks_uri":              "https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys?p={policy}",
                    "response_modes_supported": ["query","fragment","form_post"],
                "response_types_supported": ["code","id_token","code id_token"],
                "scopes_supported": ["openid"],
                "subject_types_supported": ["pairwise"],
                "id_token_signing_alg_values_supported": ["RS256"]
            }""";

    @BeforeEach
    void setUp() {
        as = new MembraneAuthorizationService();
    }

    @Nested
    class negotiateResponseMode {

        @Test
        void defaultResponseModeSingleItem() {
            assertEquals(QUERY_RESPONSE_MODE, as.negotiateResponseMode(List.of(QUERY_RESPONSE_MODE)));
            assertEquals(FRAGMENT_RESPONSE_MODE, as.negotiateResponseMode(List.of(FRAGMENT_RESPONSE_MODE)));
            assertEquals(FORM_POST_RESPONSE_MODE, as.negotiateResponseMode(List.of(FORM_POST_RESPONSE_MODE)));
        }

        @Test
        void defaultResponseModeList() {
            assertEquals(FORM_POST_RESPONSE_MODE, as.negotiateResponseMode(List.of( FRAGMENT_RESPONSE_MODE,  QUERY_RESPONSE_MODE, FORM_POST_RESPONSE_MODE)));
            assertEquals(FORM_POST_RESPONSE_MODE, as.negotiateResponseMode(List.of( FRAGMENT_RESPONSE_MODE, FORM_POST_RESPONSE_MODE)));
        }

        @Test
        void serverOfferesEmptyList() {
            assertEquals(QUERY_RESPONSE_MODE, as.negotiateResponseMode(emptyList()));
        }

        @Test
        void noMatch() {
            as.setResponseModesSupported(List.of(QUERY_RESPONSE_MODE, FORM_POST_RESPONSE_MODE));
            assertThrows(ConfigurationException.class, () -> as.negotiateResponseMode(List.of(FRAGMENT_RESPONSE_MODE)));
        }

        @Test
        void match() {
            as.setResponseModesSupported(List.of(QUERY_RESPONSE_MODE, FORM_POST_RESPONSE_MODE));
            assertEquals(QUERY_RESPONSE_MODE, as.negotiateResponseMode(List.of(QUERY_RESPONSE_MODE)));
        }
    }

    @Test
    void configDefault() throws Exception {
        MembraneAuthorizationService mas = getMock();
        mas.init();
        assertEquals(FORM_POST_RESPONSE_MODE, mas.getResponseMode());

        String url = mas.getLoginURL("/callback");
        assertTrue(url.contains("response_mode=form_post"));
        assertTrue(url.contains("redirect_uri=/callback"));
    }

    @Test
    void configQuery() throws Exception {
        MembraneAuthorizationService mas = getMock();
        mas.setResponseModesSupported(List.of(QUERY_RESPONSE_MODE, FRAGMENT_RESPONSE_MODE));
        mas.init();
        assertEquals(QUERY_RESPONSE_MODE, mas.getResponseMode());

        String url = mas.getLoginURL("/callback");
        assertTrue(url.contains("response_mode=query"));
        assertTrue(url.contains("redirect_uri=/callback"));
    }

    private @NotNull MembraneAuthorizationService getMock() throws Exception {
        MembraneAuthorizationService mas = Mockito.spy(new MembraneAuthorizationService());
        doReturn(new ByteArrayInputStream(CONFIG.getBytes())).when(mas).resolve(any(), Mockito.nullable(String.class), anyString());
        mas.setSrc("dummy");
        mas.router = new Router();
        return mas;
    }

}