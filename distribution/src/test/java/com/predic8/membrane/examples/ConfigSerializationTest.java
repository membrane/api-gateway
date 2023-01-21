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
package com.predic8.membrane.examples;

import com.predic8.beautifier.PlainBeautifierFormatter;
import com.predic8.beautifier.XMLBeautifier;
import com.predic8.membrane.annot.bean.MCUtil;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.test.AssertUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.*;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;

/**
 * Tests {@link MCUtil#fromXML(Class, String)} and {@link MCUtil#toXML(Object)} on every
 * proxies.xml file found in the "examples" directory.
 *
 * The test is:
 * 1. call fromXml() on the file.
 * 2. call toXml() on the result. (ensure that no serialization warning as been issued)
 * 3. call fromXml() on the result.
 * 4. call toXml() on the result.
 * 5. compare output from steps 2 and 4.
 *
 */
public class ConfigSerializationTest {

	// list of examples that do not work
	public static final List<String> EXCLUDED = asList("custom-interceptor", // has external classpath dependencies
			"custom-websocket-interceptor", // has external classpath dependencies
			"jdbc-database", // contains a reference to a DataSource bean (not serializable)
			"proxy", // contains more than one <router> (not supported by MCUtil#fromXML())
			"custom-interceptor-maven", // has external classpath dependencies
			"stax-interceptor", // has external classpath dependencies
			"soap", // has external classpath dependencies
			"basic-xml-interceptor", // has external classpath dependencies
			"template-interceptor");

	public static List<Object[]> getPorts() {
		ArrayList<Object[]> res = new ArrayList<>();
		recurse(new File("examples"), res);
		return res;
	}

	private static void recurse(File file, ArrayList<Object[]> res) {
		OUTER:
			for (File f : requireNonNull(file.listFiles())) {
				if (f.isDirectory())
					recurse(f, res);
				if (f.isFile() && f.getName().equals("proxies.xml")) {
					String path = f.getAbsolutePath();
					for (String exclude : EXCLUDED)
						if (path.contains(File.separator + exclude + File.separator))
							continue OUTER;
					res.add(new Object[] { path });
				}
			}
	}

	@ParameterizedTest
	@MethodSource("getPorts")
	public void doit(String configFile) throws Exception {
		try {

			String xml = readConfigFileAsXML(configFile);

			prettyPrint(xml);

			AssertUtils.assertContainsNot("incomplete serialization", xml);

			Router r2 = MCUtil.fromXML(Router.class, xml);

			String xml2 = MCUtil.toXML(r2);

			assertThat(xml2, isSimilarTo(xml));
		} catch (Exception e) {
			throw new Exception("in test " + configFile, e);
		}
	}

	private String readConfigFileAsXML(String configFile) throws IOException {
		return MCUtil.toXML(MCUtil.fromXML(Router.class, readFileToString(new File(configFile), UTF_8)));
	}

	public void prettyPrint(String xml) throws Exception {
		OutputStreamWriter outputStreamWriter = new OutputStreamWriter(System.out);
		XMLBeautifier xmlBeautifier = new XMLBeautifier(new PlainBeautifierFormatter(outputStreamWriter, 0));
		xmlBeautifier.parse(new StringReader(xml));
		outputStreamWriter.flush();
	}

}
