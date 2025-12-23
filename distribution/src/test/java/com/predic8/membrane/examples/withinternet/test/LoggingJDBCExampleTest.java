/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.concurrent.TimeUnit;

import static java.io.File.separator;
import static org.apache.commons.io.FileUtils.copyFileToDirectory;
import static org.junit.jupiter.api.Assertions.fail;

public class LoggingJDBCExampleTest extends DistributionExtractingTestcase {


    @Override
    protected String getExampleDirName() {
        return "logging" + separator + "jdbc-database";
    }

    @Test
    public void test() throws Exception {
        copyH2JarToMembraneLib();

        try (Process2 ignored = startServiceProxyScript(); HttpAssertions ha = new HttpAssertions()) {
            ha.getAndAssert200("http://localhost:2000/?t="+ System.nanoTime());
            assertLogged("GET", "/?t=", 200);
        }
    }

    private void assertLogged(String method, String pathContains, int status) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try (Connection c = DriverManager.getConnection(h2JdbcUrl(), "membrane", "membranemembrane");
                 ResultSet rs = c.prepareStatement("select * from statistic limit 200").executeQuery()) {

                ResultSetMetaData md = rs.getMetaData();
                int methodIdx = findCol(md, "METHOD", "HTTP_METHOD");
                int pathIdx   = findCol(md, "PATH", "URI", "URL");
                int statusIdx = findCol(md, "STATUS_CODE", "STATUS");

                while (rs.next()) {
                    boolean okMethod = methodIdx == 0 || method.equals(rs.getString(methodIdx));
                    boolean okPath   = pathIdx == 0   || (rs.getString(pathIdx) != null && rs.getString(pathIdx).contains(pathContains));
                    boolean okStatus = statusIdx == 0 || status == rs.getInt(statusIdx);

                    if (okMethod && okPath && okStatus) return;
                }
            } catch (SQLException ignored) {}

            Thread.sleep(100);
        }

        fail("Expected log entry not found (method=" + method + ", path~=" + pathContains + ", status=" + status + ").");
    }

    private static int findCol(ResultSetMetaData md, String... candidates) throws SQLException {
        int n = md.getColumnCount();
        for (int i = 1; i <= n; i++) {
            String name = md.getColumnLabel(i);
            if (name == null || name.isBlank()) name = md.getColumnName(i);
            String up = name == null ? "" : name.toUpperCase();
            for (String c : candidates)
                if (up.equals(c)) return i;
        }
        return 0;
    }


    private String h2JdbcUrl() {
        return "jdbc:h2:" + new File(getExampleDir(), "membranedb").getAbsolutePath().replace('\\', '/') + ";AUTO_SERVER=TRUE";
    }

    private File getExampleDir() {
        return new File(getMembraneHome(), "examples" + separator + getExampleDirName());
    }

    private void copyH2JarToMembraneLib() throws IOException {
        try {
            File jar = new File(org.h2.Driver.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            if (!jar.isFile() || !jar.getName().endsWith(".jar"))
                throw new AssertionError("H2 is not loaded from a jar: " + jar);

            copyFileToDirectory(jar, new File(getMembraneHome(), "lib"));
        } catch (Exception e) {
            throw new IOException("Failed to locate/copy H2 jar.", e);
        }
    }

}