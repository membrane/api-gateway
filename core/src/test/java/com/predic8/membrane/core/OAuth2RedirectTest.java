package com.predic8.membrane.core;

import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.ClaimList;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.StaticClientList;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.BearerTokenGenerator;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        //TODO configure
        membraneRule.getInterceptors().add(new OAuth2Resource2Interceptor());

        Rule azureRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2002), null, 0);
        azureRule.getInterceptors().add(createOAuth2AuthorizationServerInterceptor());

        Rule nginxRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2003), null, 0);
        nginxRule.getInterceptors().add(createConditionalIntercpetorWithGroovy("method == 'POST'", "exc.getResponse().setStatusCode(400)"));
        nginxRule.getInterceptors().add(createConditionalIntercpetorWithGroovy("method == 'GET'", "exc.getResponse().setStatusCode(200)"));


        membraneRouter = new Router();
        membraneRouter.setExchangeStore(new ForgetfulExchangeStore());
        membraneRouter.setTransport(new HttpTransport());
        membraneRouter.getRuleManager().addProxyAndOpenPortIfNew(membraneRule);
        membraneRouter.init();

        azureRouter = new Router();
        azureRouter.setExchangeStore(new ForgetfulExchangeStore());
        azureRouter.setTransport(new HttpTransport());
        azureRouter.getRuleManager().addProxyAndOpenPortIfNew(azureRule);
        azureRouter.init();

        nginxRouter = new Router();
        nginxRouter.setExchangeStore(new ForgetfulExchangeStore());
        nginxRouter.setTransport(new HttpTransport());
        nginxRouter.getRuleManager().addProxyAndOpenPortIfNew(nginxRule);
        nginxRouter.init();
    }

    @Test
    void testGet() {
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);
    }

    @Test
    void testPost() {
        given()
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200);
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

    //TODO configure
    private static OAuth2AuthorizationServerInterceptor createOAuth2AuthorizationServerInterceptor() {
        return new OAuth2AuthorizationServerInterceptor() {{
            setUserDataProvider(new StaticUserDataProvider() {{
                User u = new User("john", "password");
                u.getAttributes().put("aud", "demo1");
                setUsers(List.of(u));
            }});
            setClientList(new StaticClientList() {{
                setClients(List.of(new Client("abc", "def", "http://localhost:3000/oauth2callback", "authorization_code,password,client_credentials,refresh_token,implicit")));
            }});
            setTokenGenerator(new BearerTokenGenerator());
            setClaimList(new ClaimList() {{
                setValue("username");
                setScopes(new ArrayList<>() {{
                    add(new Scope() {{
                        setId("username");
                        setClaims("username");
                    }});
                }});
            }});
        }};
    }

    @AfterAll
    public static void tearDown() throws Exception {
        membraneRouter.shutdown();
        azureRouter.shutdown();
        nginxRouter.shutdown();
    }

}
