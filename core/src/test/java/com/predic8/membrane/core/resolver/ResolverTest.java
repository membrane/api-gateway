/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.resolver;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.schemavalidation.XMLSchemaValidator;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.schema.Schema;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResolverTest {

	/*
	 * The ResolverTest is a 5-dimensional parametrized test:
	 * 1. Deployment Type
	 * 2. Operating System
	 * 3. Basis URL Type
	 * 4. Relative URL Type
	 * 5. Resolver Interface used
	 *
	 * Not all combinations exist (or are currently supported). (See #setupLocations())
	 */


	// DeploymentType (STANDALONE, J2EE, OSGI) is handled differently on the tests setup/execution level

	// OperatingSystemType (WINDOWS, LINUX) is handled by Jenkins

	public enum BasisUrlType {
		HTTP,
		FILE,
		FILE_WINDOWS_DRIVE,
		CLASSPATH,
		BUNDLE,
		NAME,
		SAME_DIR,
		PARENT_DIR,
		ROOT_DIR,
		WINDOWS_DRIVE,
		WINDOWS_DRIVE_BACKSLASH
	}

	// RelativeUrlType (SCHEMA, NAME, SAME_DIR, PARENT_DIR) is handled by the test methods as well as by the test resources
	// (WSDL and XSD files referencing other files in these ways)

	// ResolverInterfaceType (MEMBRANE_SERVICE_PROXY, MEMBRANE_SOA_MODEL, LS_RESOURCE_RESOLVER) is handled by
	// different test methods below

	public static List<Object[]> getConfigurations() {
		List<Object[]> res = new ArrayList<>();
		for (BasisUrlType but : BasisUrlType.values())
			res.add(new Object[] { but });
		return res;
	}

	public static ResolverMap resolverMap = new ResolverMap();

	private String wsdlLocation;
	private String xsdLocation;

	@ParameterizedTest
	@MethodSource("getConfigurations")
	public void testLSResourceResolver(BasisUrlType basisUrlType) throws IOException {
		if (hit = !setupLocations(basisUrlType))
			return;

		try {
			new XMLSchemaValidator(resolverMap, xsdLocation, null);
		} catch (Exception e) {
			throw new RuntimeException("xsdLocation = " + xsdLocation, e);
		}
	}

	@ParameterizedTest
	@MethodSource("getConfigurations")
	public void testMembraneServiceProxyCombine(BasisUrlType basisUrlType) throws IOException {
		if (hit = !setupLocations(basisUrlType))
			return;

		assertNotNull(resolverMap.resolve(wsdlLocation));
		for (String relUrl : new String[] { "1.xsd", "./1.xsd", "../resolver/1.xsd", "http://localhost:3029/resolver/1.xsd" }) {
			try {
				assertNotNull(resolverMap.resolve(ResolverMap.combine(wsdlLocation, relUrl)));
			} catch (Exception e) {
				throw new RuntimeException("Error during combine(\"" + wsdlLocation + "\", \"" + relUrl + "\"):", e);
			}
		}
	}

	@ParameterizedTest
	@MethodSource("getConfigurations")
	public void testMembraneSoaModel(BasisUrlType basisUrlType) throws IOException {
		if (hit = !setupLocations(basisUrlType))
			return;

		try {
			WSDLParserContext ctx = new WSDLParserContext();
			ctx.setInput(wsdlLocation);
			WSDLParser wsdlParser = new WSDLParser();
			wsdlParser.setResourceResolver(resolverMap.toExternalResolver().toExternalResolver());
			Definitions definitions = wsdlParser.parse(ctx);
			for (Schema schema : definitions.getSchemas())
				schema.getElements(); // trigger lazy-loading
		} catch (Exception e) {
			throw new RuntimeException("wsdlLocation = " + xsdLocation, e);
		}
	}

	@AfterEach
	public void postpare() {
		// since a.wsdl and 2.xsd reference a HTTP resource, it should get loaded
		assertTrue(hit, "No HTTP resource was retrieved (while referenced)");
	}

	/**
	 * Sets wsdlLocation and xsdLocation, given the current test parameters
	 * @return whether the current test parameters is supported
	 */
	private boolean setupLocations(BasisUrlType basisUrlType) throws IOException {
		switch (basisUrlType) {
		case BUNDLE:
			return false;
		case CLASSPATH:
			wsdlLocation = "classpath:/resolver/a.wsdl";
			xsdLocation = "classpath:/resolver/2.xsd";
			return true;
		case FILE:
			if (!deployment.equals(STANDALONE))
				return false;
			String current = new File(".").getAbsolutePath().replaceAll("\\\\", "/");
			if (current.endsWith("."))
				current = current.substring(0, current.length()-1);
			if (current.substring(1, 3).equals(":/"))
				current = current.substring(2);
			wsdlLocation = "file://" + current + "src/test/resources/resolver/a.wsdl";
			xsdLocation = "file://" + current + "src/test/resources/resolver/2.xsd";
			return true;
		case FILE_WINDOWS_DRIVE:
			if (!deployment.equals(STANDALONE))
				return false;
			basisUrlType = BasisUrlType.WINDOWS_DRIVE;
			if (!setupLocations(basisUrlType))
				return false;
			wsdlLocation = "file://" + wsdlLocation;
			xsdLocation = "file://" + xsdLocation;
			return true;
		case WINDOWS_DRIVE:
			if (!deployment.equals(STANDALONE))
				return false;
			if (!isWindows())
				return false;
			String current2 = new File(".").getAbsolutePath().replaceAll("\\\\", "/");
			if (current2.endsWith("."))
				current2 = current2.substring(0, current2.length()-1);
			wsdlLocation = current2 + "src/test/resources/resolver/a.wsdl";
			xsdLocation = current2 + "src/test/resources/resolver/2.xsd";
			return true;
		case WINDOWS_DRIVE_BACKSLASH:
			if (!deployment.equals(STANDALONE))
				return false;
			basisUrlType = BasisUrlType.WINDOWS_DRIVE;
			if (!setupLocations(basisUrlType))
				return false;
			wsdlLocation = wsdlLocation.replaceAll("/", "\\\\");
			xsdLocation = xsdLocation.replaceAll("/", "\\\\");
			return true;
		case ROOT_DIR:
			String current3;
			if (deployment.equals(STANDALONE)) {
				current3 = new File(".").getAbsolutePath().replaceAll("\\\\", "/");
				if (current3.endsWith("."))
					current3 = current3.substring(0, current3.length()-1);
				if (current3.substring(1, 3).equals(":/"))
					current3 = current3.substring(2);
				current3 = current3 + "src/test/resources";
			} else {
				current3 = "/test";
			}
			wsdlLocation = current3 + "/resolver/a.wsdl";
			xsdLocation = current3 + "/resolver/2.xsd";
			return true;
		case HTTP:
			wsdlLocation = "http://localhost:3029/resolver/a.wsdl";
			xsdLocation = "http://localhost:3029/resolver/2.xsd";
			return true;
		case NAME:
			if (!deployment.equals(STANDALONE))
				return false; // TODO: could be implemented
			wsdlLocation = "src/test/resources/resolver/a.wsdl";
			xsdLocation = "src/test/resources/resolver/2.xsd";
			return true;
		case PARENT_DIR:
			if (!deployment.equals(STANDALONE))
				return false; // TODO: could be implemented
			wsdlLocation = "../core/src/test/resources/resolver/a.wsdl";
			xsdLocation = "../core/src/test/resources/resolver/2.xsd";
			return true;
		case SAME_DIR:
			if (!deployment.equals(STANDALONE))
				return false; // TODO: could be implemented
			wsdlLocation = "./src/test/resources/resolver/a.wsdl";
			xsdLocation = "./src/test/resources/resolver/2.xsd";
			return true;
		default:
			throw new InvalidParameterException("basisUrlType = " + basisUrlType);
		}
	}

	public static boolean isWindows() {
		return System.getProperty("os.name").contains("Windows");
	}

	static HttpRouter router = new HttpRouter();
	static volatile boolean hit = false;

	public static final String STANDALONE = "standalone";
	public static final String J2EE = "J2EE";

	public static String deployment = STANDALONE;

	@BeforeAll
	public static void setup() throws Exception {
		ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3029), "localhost", 8080);

		sp.getInterceptors().add(new AbstractInterceptor() {
			@Override
			public Outcome handleRequest(Exchange exc) throws Exception {
				hit = true;
				return Outcome.CONTINUE;
			}
		});

		WebServerInterceptor i = new WebServerInterceptor();
		if (deployment.equals(STANDALONE))
			i.setDocBase("src/test/resources");
		else {
			i.setDocBase("/test");
			router.getResolverMap().addSchemaResolver(resolverMap.getFileSchemaResolver());
		}
		sp.getInterceptors().add(i);

		router.add(sp);
		router.init();
	}

	@AfterAll
	public static void teardown() throws IOException {
		router.shutdown();
	}

}
