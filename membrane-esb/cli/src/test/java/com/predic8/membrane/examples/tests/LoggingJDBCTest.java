package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;
import static org.apache.commons.io.FileUtils.copyFileToDirectory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class LoggingJDBCTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		copyDerbyJarToMembraneLib();
		
		File baseDir = getExampleDir("logging-jdbc");
		File beansConfig = new File(baseDir, "jdbc-beans.xml");
		FileUtils.writeStringToFile(beansConfig, FileUtils.readFileToString(beansConfig).
				replace("org.apache.derby.jdbc.ClientDriver", "org.apache.derby.jdbc.EmbeddedDriver").
				replace("jdbc:derby://localhost:1527/membranedb;create=true", "jdbc:derby:derbyDB;create=true")
				);
		
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
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
					Assert.assertTrue(rs.next());
					Assert.assertEquals("GET", rs.getString(1));
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
		File derbyJar = new File(classJar.split("!")[0].substring(6));

		if (!derbyJar.exists())
			throw new AssertionError("derby jar not found in classpath (it's either missing or the detection logic broken). classJar=" + classJar);

		copyFileToDirectory(derbyJar, new File(getMembraneHome(), "lib"));
	}


}
