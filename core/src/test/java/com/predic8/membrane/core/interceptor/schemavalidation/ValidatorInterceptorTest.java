/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class ValidatorInterceptorTest {

    private static Request requestTB;

    private static Request requestXService;

    private static Exchange exc;

    private static Router router;

    public static final String ARTICLE_SERVICE_WSDL = "classpath:/validation/ArticleService.wsdl";

    public static final String ARTICLE_SERVICE_BOM_WSDL = "classpath:/validation/ArticleService-bom.xml";

    public static final String BLZ_SERVICE_WSDL = "classpath:/validation/BLZService.xml";

    public static final String E_MAIL_SERVICE_WSDL = "classpath:/validation/XWebEmailValidation.wsdl.xml";


    @BeforeAll
    public static void setUp() throws URISyntaxException {
        requestTB = Request.post("http://thomas-bayer.com").build();
        requestXService = Request.post("http://ws.xwebservices.com").build();
        exc = new Exchange(null);
        router = new Router();
    }

    @Test
    void testHandleRequestValidBLZMessage() throws Exception {
        assertEquals(CONTINUE, getOutcome(requestTB, createValidatorInterceptor(BLZ_SERVICE_WSDL), getPathFromResource("/getBank.xml")));
    }

    @Test
    void testHandleRequestInvalidBLZMessage() throws Exception {
        assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(BLZ_SERVICE_WSDL), getPathFromResource("/getBankInvalid.xml")));
    }

    @Test
    void testHandleRequestValidArticleMessage() throws Exception {
        assertEquals(CONTINUE, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), getPathFromResource("/validation/articleRequest.xml")));
    }

    @Test
    void testHandleRequestValidArticleMessageBOM() throws Exception {
        assertEquals(CONTINUE, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_BOM_WSDL), getPathFromResource("/validation/articleRequest-bom.xml")));
    }

    @Test
    void testHandleNonSOAPXMLMessage() throws Exception {
        assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), getPathFromResource("/customer.xml")));
    }

    @Test
    void testHandleRequestInvalidArticleMessage() throws Exception {
        assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), getPathFromResource("/validation/articleRequestInvalid.xml")));
    }

    @Test
    void testHandleRequestInvalidArticleMessageBOM() throws Exception {
        assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_BOM_WSDL), getPathFromResource("/validation/articleRequestInvalid-bom.xml")));
    }

    @Test
    void testHandleResponseValidArticleMessage() throws Exception {
        exc.setRequest(requestTB);
        exc.setResponse(Response.ok().body(getContent(getPathFromResource("/validation/articleResponse.xml"))).build());
        assertEquals(CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL).handleResponse(exc));
    }

    @Test
    void testHandleResponseValidArticleMessageGzipped() throws Exception {
        exc.setRequest(requestTB);
        exc.setResponse(Response.ok().body(getContent(getPathFromResource("/validation/articleResponse.xml.gz"))).header("Content-Encoding", "gzip").build());
        assertEquals(CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL).handleResponse(exc));
    }

    @Test
    void testHandleResponseValidArticleMessageBrotli() throws Exception {
        exc.setRequest(requestTB);
        exc.setResponse(Response.ok().body(getContent(getPathFromResource("/validation/articleResponse.xml.br"))).header("Content-Encoding", "br").build());
        assertEquals(CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL).handleResponse(exc));
    }

    @Test
    void testHandleRequestValidEmailMessage() throws Exception {
        assertEquals(CONTINUE, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), getPathFromResource("/validation/validEmail.xml")));
    }

    @Test
    void testHandleRequestInvalidEmailMessageDoubleEMailElement() throws Exception {
        assertEquals(ABORT, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), getPathFromResource("/validation/invalidEmail.xml")));
    }

    @Test
    void testHandleRequestInvalidEmailMessageDoubleRequestElement() throws Exception {
        assertEquals(ABORT, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), getPathFromResource("/validation/invalidEmail2.xml")));
    }

    @Test
    void testHandleRequestInvalidEmailMessageUnknownElement() throws Exception {
        assertEquals(ABORT, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), getPathFromResource("validation/invalidEmail3.xml")));
    }

    @Test
    void testSchemaValidation() throws Exception {
        assertEquals(CONTINUE, getOutcome(requestTB, createSchemaValidatorInterceptor(getPathFromResource("/validation/order.xsd")), getPathFromResource("validation/order.xml")));
        assertEquals(ABORT, getOutcome(requestTB, createSchemaValidatorInterceptor(getPathFromResource("/validation/order.xsd")), getPathFromResource("validation/invalid-order.xml")));
    }

    private Outcome getOutcome(Request request, Interceptor interceptor, String fileName) throws Exception {
        request.setBodyContent(getContent(fileName));
        exc.setRequest(request);
        return interceptor.handleRequest(exc);
    }

    private byte[] getContent(String fileName) throws IOException {
        return Files.readAllBytes(Path.of(fileName));
    }

    private ValidatorInterceptor createSchemaValidatorInterceptor(String schema) {
        ValidatorInterceptor interceptor = new ValidatorInterceptor();
        interceptor.setResourceResolver(new ResolverMap());
        interceptor.setSchema(schema);
        interceptor.init(router);
        return interceptor;
    }

    private ValidatorInterceptor createValidatorInterceptor(String wsdl) {
        ValidatorInterceptor interceptor = new ValidatorInterceptor();
        interceptor.setResourceResolver(new ResolverMap());
        interceptor.setWsdl(wsdl);
        interceptor.init(router);
        return interceptor;
    }

}
