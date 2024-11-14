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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import org.apache.commons.io.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;


public class ValidatorInterceptorXmlBomTest {

	public static final String XML_BOM = "/validation/xml-bom";

	public static final String ARTICLE_REQUEST_INVALID_XML_BOM = XML_BOM +  "/articleRequestInvalid-bom.xml";

	public static final String ARTICLE_REQUEST_XML_BOM = XML_BOM + "/articleRequest.xml";
	public static final String ARTICLE_RESPONSE_XML_BOM = XML_BOM + "/articleResponse.xml";
	public static final String ORDER_BOM_XSD = XML_BOM + "/order-bom.xsd";
	public static final String ORDER_BOM_XML = XML_BOM + "/order-bom.xml";

	public static final String INVALID_ORDER_BOM_XSD = XML_BOM + "/invalid-order-bom.xml";

	private Request requestTB;

	private Exchange exc;



	public static final String ARTICLE_SERVICE_WSDL_BOM = "src/test/resources/validation/xml-bom/ArticleService-bom.xml";

	@BeforeEach
	public void setUp() throws Exception {
		requestTB = MessageUtil.getPostRequest("http://thomas-bayer.com");
		exc = new Exchange(null);
	}

	@Test
	public void testHandleRequestValidArticleMessage() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL_BOM), ARTICLE_REQUEST_XML_BOM));
	}

	@Test
	public void testHandleRequestValidArticleMessageBOM() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL_BOM), ARTICLE_REQUEST_XML_BOM));
	}

	@Test
	public void testHandleNonSOAPXMLMessage() throws Exception {
		assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL_BOM), "/customer.xml"));
	}

	@Test
	public void testHandleRequestInvalidArticleMessage() throws Exception {
		assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL_BOM), ARTICLE_REQUEST_INVALID_XML_BOM));
	}

	@Test
	public void testHandleRequestInvalidArticleMessageBOM() throws Exception {

		assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL_BOM), ARTICLE_REQUEST_INVALID_XML_BOM));
	}

	@Test
	public void testHandleResponseValidArticleMessage() throws Exception {
		exc.setRequest(requestTB);
		exc.setResponse(Response.ok().body(getContent(ARTICLE_RESPONSE_XML_BOM)).build());
		assertEquals(CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL_BOM).handleResponse(exc));
	}

	@Test
	public void testHandleResponseValidArticleMessageGzipped() throws Exception {
		exc.setRequest(requestTB);
		exc.setResponse(Response.ok().body(getContent("/validation/articleResponse.xml.gz")).header("Content-Encoding", "gzip").build());
		assertEquals(CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL_BOM).handleResponse(exc));
	}

	@Test
	public void testSchemaValidation2() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestTB, createSchemaValidatorInterceptor("src/test/resources/validation/order.xsd"), "/validation/order.xml"));
		assertEquals(Outcome.ABORT, getOutcome(requestTB, createSchemaValidatorInterceptor("src/test/resources/validation/order.xsd"), "/validation/invalid-order.xml"));
	}

	@Test
	public void testSchemaValidation() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestTB, createSchemaValidatorInterceptor(ORDER_BOM_XSD), ORDER_BOM_XML));
		assertEquals(ABORT, getOutcome(requestTB, createSchemaValidatorInterceptor(ORDER_BOM_XSD), INVALID_ORDER_BOM_XSD));
	}

	private Outcome getOutcome(Request request, Interceptor interceptor, String fileName) throws Exception {
		request.setBodyContent(getContent(fileName));
		exc.setRequest(request);
		return interceptor.handleRequest(exc);
	}

	private byte[] getContent(String fileName) throws IOException {
		return IOUtils.toByteArray(this.getClass().getResourceAsStream(fileName));
	}

	private ValidatorInterceptor createSchemaValidatorInterceptor(String schema) throws Exception {
		ValidatorInterceptor interceptor = new ValidatorInterceptor();
		interceptor.setResourceResolver(new ResolverMap());
		interceptor.setSchema(schema);
		interceptor.init();
		return interceptor;
	}

	private ValidatorInterceptor createValidatorInterceptor(String wsdl) throws Exception {
		ValidatorInterceptor interceptor = new ValidatorInterceptor();
		interceptor.setResourceResolver(new ResolverMap());
		interceptor.setWsdl(wsdl);
		interceptor.init();
		return interceptor;
	}

}
