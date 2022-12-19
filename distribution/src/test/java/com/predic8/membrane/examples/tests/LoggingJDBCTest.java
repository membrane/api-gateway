/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static org.apache.commons.io.FileUtils.copyFileToDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class LoggingJDBCTest extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		copyDerbyJarToMembraneLib();

		File baseDir = getExampleDir("logging-jdbc");
		File beansConfig = new File(baseDir, "proxies.xml");
		FileUtils.writeStringToFile(beansConfig, FileUtils.readFileToString(beansConfig).
				replace("org.apache.derby.jdbc.ClientDriver", "org.apache.derby.jdbc.EmbeddedDriver").
				replace("jdbc:derby://localhost:1527/membranedb;create=true", "jdbc:derby:derbyDB;create=true")
				);

		Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();
		try {
			getAndAssert200("http://localhost:2000/");
		} finally {
			sl.killScript();
		}

		assertLogToDerbySucceeded(baseDir);
	}

	private void assertLogToDerbySucceeded(File baseDir)
			throws InstantiationException, IllegalAccessException,
			ClassNotFoundException, SQLException {
		String driver = "org.apache.derby.jdbc.EmbeddedDriver";
		Class.forName(driver).newInstance();

		File db = new File(baseDir, "derbyDB");
		Connection conn = DriverManager.getConnection("jdbc:derby:" + db.getAbsolutePath().replace("\\", "/"));
		try {
			Statement stmt = conn.createStatement();
			try {
				ResultSet rs = stmt.executeQuery("select METHOD from MEMBRANE.STATISTIC");
				try {
					assertTrue(rs.next());
					assertEquals("GET", rs.getString(1));
				} finally {
					rs.close();
				}
			} finally {
				stmt.close();
			}
		} finally {
			conn.close();
		}

		try {
			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		} catch (SQLException e) {
			// do nothing
		}
	}

	private void copyDerbyJarToMembraneLib() throws IOException {
		String classJar = getClass().getResource("/" + "org.apache.derby.jdbc.EmbeddedDriver".replace('.', '/') + ".class").getPath();
		File derbyJar = new File(classJar.split("!")[0].substring(Process2.isWindows() ? 6 : 5));

		if (!derbyJar.exists())
			throw new AssertionError("derby jar not found in classpath (it's either missing or the detection logic broken). classJar=" + classJar);

		copyFileToDirectory(derbyJar, new File(getMembraneHome(), "lib"));
	}


}
