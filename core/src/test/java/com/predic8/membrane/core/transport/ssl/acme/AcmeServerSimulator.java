/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.ssl.acme;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.jetbrains.annotations.*;
import org.jose4j.base64url.Base64;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.io.Resources.getResource;
import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.Header.USER_AGENT;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jose4j.lang.HashUtil.SHA_256;
import static org.junit.jupiter.api.Assertions.*;

public class AcmeServerSimulator {
    private static final Logger LOG = LoggerFactory.getLogger(AcmeServerSimulator.class);
    private final int port;
    private final int challengePort;
    private final boolean actuallyPerformChallenge;
    private final AtomicReference<String> theNonce = new AtomicReference<>();
    private final HttpClient hc = new HttpClient();
    private final AtomicBoolean challengeSucceeded = new AtomicBoolean();
    private HttpRouter router;
    private final AcmeCASimulation ca = new AcmeCASimulation();
    private String certificates;
    private final AtomicReference<String> orderStatus = new AtomicReference<>("pending");

    public AcmeServerSimulator(int port, int challengePort, boolean actuallyPerformChallenge) {
        this.port = port;
        this.challengePort = challengePort;
        this.actuallyPerformChallenge = actuallyPerformChallenge;

        ca.init();
    }

    public void start() throws IOException {
        router = new HttpRouter();
        router.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(port), "localhost", 80);
        sp.getInterceptors().add(new AbstractInterceptor() {
            final ObjectMapper om = new ObjectMapper();

            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                LOG.debug("acme server: got " + exc.getRequest().getUri() + " request");
                if ("/directory".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok()
                            .contentType(APPLICATION_JSON)
                            .body(Resources.toString(getResource("acme/directory.json"), UTF_8))
                            .build());
                    return RETURN;
                }
                if ("/acme/new-nonce".equals(exc.getRequest().getUri()) && "HEAD".equals(exc.getRequest().getMethod())) {
                    exc.setResponse(Response.ok()
                            .header("Replay-Nonce", createNonce())
                            .body("")
                            .build());
                    return RETURN;
                }
                assertTrue(isOfMediaType(APPLICATION_JOSE_JSON, exc.getRequest().getHeader().getFirstValue(CONTENT_TYPE)));
                assertNotNull(exc.getRequest().getHeader().getFirstValue(USER_AGENT));

                MyJsonWebSignature jws = getMyJsonWebSignature(exc);

                assertTrue(jws.getJwkHeader() == null || jws.getKeyIdHeaderValue() == null, "RFC 8555 Section 6.2");

                if ("/acme/new-acct".equals(exc.getRequest().getUri())) {
                    // if new account: use public key to verify JWS
                    jws.setKey(jws.getJwkHeader().getKey());
                } else {
                    // elsewise: use key id to look up key
                    synchronized (keys) {
                        jws.setKey(keys.get(jws.getKeyIdHeaderValue()).getKey());
                    }
                }

                jws.verifySignature();

                if (!checkNonce(jws)) {
                    exc.setResponse(Response.badRequest()
                            .header("Replay-Nonce", createNonce())
                            .header(CONTENT_TYPE, APPLICATION_PROBLEM_JSON)
                            .body(om.writeValueAsString(ImmutableMap.of(
                                    "type", "urn:ietf:params:acme:error:badNonce" ,
                                    "detail", "Bad Nonce: "+jws
                            ))
                    ).build());
                    return RETURN;
                }
                if ("/acme/new-acct".equals(exc.getRequest().getUri())) {
                    assertNotNull(jws.getJwkHeader(), "RFC 8555 Section 6.2");
                    String accountUrl = "http://localhost:3050/acme/acct/123456";

                    exc.setResponse(Response.ok().status(201, "Created")
                            .contentType(APPLICATION_JSON)
                            .header("Location", accountUrl)
                            .header("Replay-Nonce", createNonce())
                            .body(Resources.toString(getResource("acme/new-account-created.json"), UTF_8))
                            .build());

                    synchronized (keys) {
                        keys.put(accountUrl, jws.getJwkHeader());
                    }

                    return RETURN;
                }
                assertNotNull("RFC 8555 Section 6.2", jws.getKeyIdHeaderValue());
                if ("/acme/new-order".equals(exc.getRequest().getUri())) {
                    // here we spuriously return 'badNonce' to check whether the client correctly retries with the
                    // nonce returned on the response
                    if (theNonce.get() == null || !theNonce.get().equals(jws.getHeader("nonce"))) {
                        String nonce = createNonce();
                        theNonce.set(nonce);
                        exc.setResponse(Response.badRequest()
                                .header("Replay-Nonce", nonce)
                                .header(CONTENT_TYPE, APPLICATION_PROBLEM_JSON)
                                .body(om.writeValueAsString(ImmutableMap.of(
                                        "type", "urn:ietf:params:acme:error:badNonce" ,
                                        "detail", "Bad Nonce: "+jws
                                ))
                        ).build());
                        return RETURN;
                    }

                    exc.setResponse(Response.ok().status(201, "Created")
                            .contentType(APPLICATION_JSON)
                            .header("Replay-Nonce", createNonce())
                            .header("Location", "http://localhost:3050/acme/order/42212345")
                            .body(Resources.toString(getResource("acme/new-order-created.json"), UTF_8))
                            .build());

                    return RETURN;
                }
                if ("/acme/order/42212345".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok()
                            .contentType(APPLICATION_JSON)
                            .header("Replay-Nonce", createNonce())
                            .body(Resources.toString(getResource("acme/order-"+orderStatus.get()+".json"), UTF_8))
                            .build());

                    return RETURN;
                }
                if ("/acme/authz-v3/151234567".equals(exc.getRequest().getUri())) {
                    if (challengeSucceeded.get()) {
                        exc.setResponse(Response.ok()
                                .contentType(APPLICATION_JSON)
                                .header("Replay-Nonce", createNonce())
                                .body(Resources.toString(getResource("acme/authorization-valid.json"), UTF_8))
                                .build());
                    } else {
                        exc.setResponse(Response.ok()
                                .contentType(APPLICATION_JSON)
                                .header("Replay-Nonce", createNonce())
                                .body(Resources.toString(getResource("acme/authorization.json"), UTF_8))
                                .build());
                    }
                    return RETURN;
                }
                if ("/acme/chall-v3/1555123456/abCd1E".equals(exc.getRequest().getUri())) {
                    startChallenge(jws.getKeyIdHeaderValue());

                    exc.setResponse(Response.ok()
                            .contentType(APPLICATION_JSON)
                            .header("Replay-Nonce", createNonce())
                            .body(Resources.toString(getResource("acme/challenge-pending.json"), UTF_8))
                            .build());

                    return RETURN;
                }
                if ("/acme/finalize/42212345/1661234567".equals(exc.getRequest().getUri())) {
                    certificates = ca.sign((String) om.readValue(jws.getPayload(), Map.class).get("csr"));
                    orderStatus.set("valid");

                    exc.setResponse(Response.ok()
                            .contentType(APPLICATION_JSON)
                            .header("Replay-Nonce", createNonce())
                            .body(Resources.toString(getResource("acme/order-processing.json"), UTF_8))
                            .build());

                    return RETURN;
                }
                if ("/acme/cert/fab123456789abcdef0123456789abcdef12".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok()
                            .contentType(APPLICATION_JSON)
                            .header("Replay-Nonce", createNonce())
                            .body(certificates)
                            .build());

                    return RETURN;
                }

                exc.setResponse(Response.notFound().build());
                return RETURN;
            }

            @NotNull
            private MyJsonWebSignature getMyJsonWebSignature(Exchange exc) throws IOException, JoseException {
                @SuppressWarnings("unchecked")
                Map<String,String> body = om.readValue(exc.getRequest().getBodyAsStreamDecoded(), Map.class);
                MyJsonWebSignature jws = new MyJsonWebSignature();
                jws.setEncodedHeader(body.get("protected"));
                jws.setEncodedPayload(body.get("payload"));
                jws.setEncodedSignature(body.get("signature"));
                return jws;
            }
        });
        router.add(sp);

        router.start();
    }

    private void startChallenge(String keyId) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000);
                if (actuallyPerformChallenge) {
                    String token = "79123-1234itunehnhtudixnhudindih-34hty5dn74";
                    Exchange e = hc.call(new Request.Builder().get("http://localhost:" + challengePort + "/.well-known/acme-challenge/" + token).buildExchange());
                    assertEquals(200, e.getResponse().getStatusCode());
                    String result = e.getResponse().getBodyAsStringDecoded();
                    assertTrue(result.startsWith(token + "."));
                    String hash = result.substring(token.length() + 1);
                    PublicJsonWebKey jwk;
                    synchronized (keys) {
                        jwk = keys.get(keyId);
                    }
                    String expectedHash = jwk.calculateBase64urlEncodedThumbprint(SHA_256);
                    assertEquals(expectedHash, hash);
                }
                challengeSucceeded.set(true);
                orderStatus.set("ready");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.setName("ACME Challenger");
        t.start();
    }

    private boolean checkNonce(JsonWebSignature jws) {
        String nonce = jws.getHeader("nonce");
        synchronized (nonces) {
            if (nonces.remove(nonce))
                return true;
        }
        return false;
    }

    final Random random = new Random();
    final Set<String> nonces = new HashSet<>();
    final HashMap<String, PublicJsonWebKey> keys = new HashMap<>();

    private String createNonce() {
        byte[] buf = new byte[20];
        random.nextBytes(buf);
        String nonce = Base64.encode(buf);
        synchronized (nonces) {
            nonces.add(nonce);
        }
        return nonce;
    }

    public void stop() {
        router.stop();
    }

    public AcmeCASimulation getCA() {
        return ca;
    }

    private static class MyJsonWebSignature extends JsonWebSignature {
        @Override
        public void setEncodedHeader(String encodedHeader) throws JoseException {
            super.setEncodedHeader(encodedHeader);
        }

        @Override
        public void setSignature(byte[] signature) {
            super.setSignature(signature);
        }

        public void setEncodedSignature(String signature) {
            setSignature(this.base64url.base64UrlDecode(signature));
        }
    }
}
