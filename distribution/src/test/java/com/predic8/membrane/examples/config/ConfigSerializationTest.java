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
package com.predic8.membrane.examples.config;

import com.predic8.beautifier.PlainBeautifierFormatter;
import com.predic8.beautifier.XMLBeautifier;
import com.predic8.membrane.annot.bean.MCUtil;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.test.AssertUtils;
import org.apache.commons.io.FileUtils;
import org.custommonkey.xmlunit.XMLAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
@RunWith(Parameterized.class)
public class ConfigSerializationTest {

	// list of examples that do not work
	public static List<String> EXCLUDED = Arrays.asList(new String[] {
			"custom-interceptor", // has external classpath dependencies
			"custom-websocket-interceptor", // has external classpath dependencies
			"logging-jdbc", // contains a reference to a DataSource bean (not serializable)
			"proxy", // contains more than one <router> (not supported by MCUtil#fromXML())
			"custom-interceptor-maven", // has external classpath dependencies
			"stax-interceptor", // has external classpath dependencies
			"soap", // has external classpath dependencies
			"basic-xml-interceptor", // has external classpath dependencies
			"template-interceptor"
	});

	@Parameters
	public static List<Object[]> getPorts() {
		ArrayList<Object[]> res = new ArrayList<Object[]>();
		recurse(new File("examples"), res);
		return res;
	}

	private static void recurse(File file, ArrayList<Object[]> res) {
		OUTER:
			for (File f : file.listFiles()) {
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

	private final String configFile;

	public ConfigSerializationTest(String configFile) {
		this.configFile = configFile;
	}

	@Test
	public void doit() throws Exception {
		try {
			String config = FileUtils.readFileToString(new File(configFile));

			Object o = MCUtil.fromXML(Router.class, config);

			String xml = MCUtil.toXML(o);

			//prettyPrint(xml);
			//System.out.println(xml);

			//System.out.println("ConfigFile: " + configFile);

			AssertUtils.assertContainsNot("incomplete serialization", xml);

			Router r2 = MCUtil.fromXML(Router.class, xml);

			String xml2 = MCUtil.toXML(r2);

			XMLAssert.assertXMLEqual(xml2, xml);
		} catch (Exception e) {
			throw new Exception("in test " + configFile, e);
		}
	}

	public void prettyPrint(String xml) throws Exception, IOException {
		OutputStreamWriter outputStreamWriter = new OutputStreamWriter(System.out);
		XMLBeautifier xmlBeautifier = new XMLBeautifier(new PlainBeautifierFormatter(outputStreamWriter, 0));
		xmlBeautifier.parse(new StringReader(xml));
		outputStreamWriter.flush();
	}

}
