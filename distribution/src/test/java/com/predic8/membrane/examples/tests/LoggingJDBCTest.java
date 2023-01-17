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

import com.predic8.membrane.core.util.*;
import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.sql.*;

import static com.predic8.membrane.core.util.OSUtil.isWindows;
import static com.predic8.membrane.test.AssertUtils.*;
import static java.io.File.separator;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class LoggingJDBCTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "logging" + separator + "jdbc-database";
    }

    @Test
    public void test() throws Exception {
        copyDerbyLibraries();

        writeStringToFile(new File(baseDir, "proxies.xml"),  readFileFromBaseDir("proxies.xml").
                replace("org.apache.derby.jdbc.ClientDriver", "org.apache.derby.jdbc.EmbeddedDriver").
                replace("jdbc:derby://localhost:1527/membranedb;create=true", "jdbc:derby:derbyDB;create=true"), UTF_8);

        try (Process2 ignored = startServiceProxyScript()) {
            getAndAssert200("http://localhost:2000/");
        }

        assertLogToDerbySucceeded();
    }

    private void copyDerbyLibraries() throws IOException {
        copyDerbyJarToMembraneLib("org.apache.derby.jdbc.EmbeddedDriver");
        copyDerbyJarToMembraneLib("org.apache.derby.iapi.jdbc.JDBCBoot");
        copyDerbyJarToMembraneLib("org.apache.derby.shared.common.error.StandardException");
    }

    private void assertLogToDerbySucceeded() throws Exception {
        sleep(1000); // We have to wait till the Membrane process is terminated, otherwise the derbyDB file is still used by Membrane
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();

        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                //noinspection SqlResolve,SqlNoDataSourceInspection
                try (ResultSet rs = stmt.executeQuery("select METHOD from MEMBRANE.STATISTIC")) {
                    assertTrue(rs.next());
                    assertEquals("GET", rs.getString(1));
                }
            }
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:derby:" + getDBFile("derbyDB").getAbsolutePath().replace("\\", "/"));
    }

    private File getDBFile(String derbyDB) {
        return new File(baseDir, derbyDB);
    }

    private void copyDerbyJarToMembraneLib(String clazz) throws IOException {

        File derbyJar = getDerbyJarFile(getClassJar(clazz));

        if (!derbyJar.exists())
            throw new AssertionError("derby jar not found in classpath (it's either missing or the detection logic broken). classJar=" + getClassJar(clazz));

        copyFileToDirectory(derbyJar, new File(getMembraneHome(), "lib"));
    }

    private String getClassJar(String clazz) {
        return requireNonNull(getClass().getResource("/" + clazz.replace('.', '/') + ".class")).getPath();
    }

    private File getDerbyJarFile(String classJar) {
        return new File(classJar.split("!")[0].substring(isWindows() ? 6 : 5));
    }
}