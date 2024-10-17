package com.predic8.membrane.core;

import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2.client.b2c.MockAuthorizationServer;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.oauth2client.SessionOriginalExchangeStore;
import com.predic8.membrane.core.interceptor.session.InMemorySessionManager;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.SPEL;
import static io.restassured.RestAssured.given;

public class OAuth2RedirectTest {

    static Router membraneRouter;
    static Router azureRouter;
    static Router nginxRouter;

    @BeforeAll
    static void setup() throws Exception {

        Rule membraneRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2000), null, 0);
        membraneRule.getInterceptors().add(new OAuth2Resource2Interceptor() {{
            setSessionManager(new InMemorySessionManager());
            setAuthService(new MembraneAuthorizationService() {{
                setSrc("http://localhost:2002");
                setClientSecret("def");
                setClientId("abc");
                setScope("openid profile offline_access");
                setSubject("sub");
            }});
            setOriginalExchangeStore(new SessionOriginalExchangeStore());
        }});

        Rule azureRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2002), null, 0);
        azureRule.getInterceptors().add(new ConditionalInterceptor() {{
            setTest("path matches '/.well-known/openid-configuration'");
            setLanguage(SPEL);
            setInterceptors(List.of(
                new GroovyInterceptor() {{
                    setSrc("""
                            import groovy.json.JsonOutput
                            
                            def config = [
                                issuer: 'http://localhost:2002',
                                authorization_endpoint: 'http://localhost:2002/authorize',
                                token_endpoint: 'http://localhost:2002/token',
                                userinfo_endpoint: 'http://localhost:2002/userinfo',
                                jwks_uri: 'http://localhost:2002/jwks',
                                scopes_supported: ['openid', 'profile', 'offline_access'],
                                response_types_supported: ['code'],
                                subject_types_supported: ['public'],
                                id_token_signing_alg_values_supported: ['RS256'],
                                token_endpoint_auth_methods_supported: ['client_secret_post']
                            ]
                            
                            exc.response.contentType = 'application/json'
                            exc.response.statusCode = 200
                            exc.response.body = JsonOutput.toJson(config).bytes"""
                    );
                }}
            ));
        }});

        azureRule.getInterceptors().add(new ConditionalInterceptor() {{
            setTest("path == '/token'");  // Mocking the token endpoint
            setLanguage(SPEL);
            setInterceptors(List.of(
                    new GroovyInterceptor() {{
                        setSrc("""
                import groovy.json.JsonOutput;
                
                def tokenResponse = [
                    access_token: 'mock-access-token',
                    token_type: 'Bearer',
                    expires_in: 3600,
                    refresh_token: 'mock-refresh-token',
                    scope: 'openid profile offline_access'
                ];
                
                exc.response.contentType = 'application/json';
                exc.response.statusCode = 200;
                exc.response.body = JsonOutput.toJson(tokenResponse).bytes;
            """);
                    }}
            ));
        }});


        Rule nginxRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2003), null, 0);
        nginxRule.getInterceptors().add(createConditionalIntercpetorWithGroovy("method == 'POST'", "exc.getResponse().setStatusCode(400)"));
        nginxRule.getInterceptors().add(createConditionalIntercpetorWithGroovy("method == 'GET'", "exc.getResponse().setStatusCode(200)"));

        membraneRouter = new Router();
        membraneRouter.setExchangeStore(new ForgetfulExchangeStore());
        membraneRouter.setTransport(new HttpTransport());
        membraneRouter.getRuleManager().addProxyAndOpenPortIfNew(membraneRule);
        membraneRouter.init();
        membraneRouter.start();

        azureRouter = new Router();
        azureRouter.setExchangeStore(new ForgetfulExchangeStore());
        azureRouter.setTransport(new HttpTransport());
        azureRouter.getRuleManager().addProxyAndOpenPortIfNew(azureRule);
        azureRouter.init();
        azureRouter.start();

        nginxRouter = new Router();
        nginxRouter.setExchangeStore(new ForgetfulExchangeStore());
        nginxRouter.setTransport(new HttpTransport());
        nginxRouter.getRuleManager().addProxyAndOpenPortIfNew(nginxRule);
        nginxRouter.init();
        nginxRouter.start();
    }

    @Test
    void testGet() {
        given()
            .auth().oauth2("mock-access-token")
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);
    }

    @Test
    void testPost() {
        given()
            .auth().oauth2("mock-access-token")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400);
    }

    private static ConditionalInterceptor createConditionalIntercpetorWithGroovy(String test, String groovy) {
        return new ConditionalInterceptor() {{
            setLanguage(SPEL);
            setTest(test);
            setInterceptors(List.of(new GroovyInterceptor() {{
                setSrc(groovy);
            }}));
        }};
    }
MockAuthorizationServer
    @AfterAll
    public static void tearDown() {
        membraneRouter.stop();
        azureRouter.stop();
        nginxRouter.stop();
    }

}
