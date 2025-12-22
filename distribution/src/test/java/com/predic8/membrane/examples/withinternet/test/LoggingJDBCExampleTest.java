package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.util.OSUtil.isWindows;
import static java.io.File.separator;
import static java.util.Objects.requireNonNull;
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
            String path = "/?t=" + System.nanoTime();
            ha.getAndAssert200("http://localhost:2000" + path);
            assertLogged("GET", "/?t=", 200);
        }
    }


    private void assertLogged(String method, String pathContains, int status) throws Exception {
        Class.forName("org.h2.Driver");

        String url = h2JdbcUrl();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            try (Connection c = DriverManager.getConnection(url, "membrane", "membranemembrane")) {
                var cols = new java.util.HashSet<String>();
                try (ResultSet rs = c.getMetaData().getColumns(null, null, "STATISTIC", null)) {
                    while (rs.next()) cols.add(rs.getString("COLUMN_NAME").toUpperCase());
                }
                if (cols.isEmpty()) throw new SQLException("STATISTIC not ready");

                String methodCol = cols.contains("METHOD") ? "method" :
                        cols.contains("HTTP_METHOD") ? "http_method" : null;
                String pathCol   = cols.contains("PATH") ? "path" :
                        cols.contains("URI") ? "uri" :
                                cols.contains("URL") ? "url" : null;
                String statusCol = cols.contains("STATUS_CODE") ? "status_code" :
                        cols.contains("STATUS") ? "status" : null;

                StringBuilder sql = new StringBuilder("select count(*) from statistic where 1=1");
                if (methodCol != null) sql.append(" and ").append(methodCol).append("=?");
                if (pathCol != null)   sql.append(" and ").append(pathCol).append(" like ?");
                if (statusCol != null) sql.append(" and ").append(statusCol).append("=?");

                try (var ps = c.prepareStatement(sql.toString())) {
                    int i = 1;
                    if (methodCol != null) ps.setString(i++, method);
                    if (pathCol != null)   ps.setString(i++, "%" + pathContains + "%");
                    if (statusCol != null) ps.setInt(i++, status);

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getLong(1) > 0) return;
                    }
                }

            } catch (SQLException e) {
                // retry
            }
            Thread.sleep(100);
        }

        fail("Expected log entry not found (method=" + method + ", path~=" + pathContains + ", status=" + status + ").");
    }


    private String h2JdbcUrl() {

        File dbBase = new File(getExampleDir(), "membranedb");
        String abs = dbBase.getAbsolutePath().replace('\\', '/');
        return "jdbc:h2:" + abs + ";AUTO_SERVER=TRUE";
    }

    private File getExampleDir() {
        return new File(getMembraneHome(), "examples" + separator + getExampleDirName());
    }

    private void copyH2JarToMembraneLib() throws IOException {
        URL res = requireNonNull(getClass().getResource("/org/h2/Driver.class"));
        String u = res.toString(); // jar:file:/.../h2-<ver>.jar!/org/h2/Driver.class

        String jarUrl = u.substring(0, u.indexOf('!'));
        if (jarUrl.startsWith("jar:")) jarUrl = jarUrl.substring(4);
        if (jarUrl.startsWith("file:")) jarUrl = jarUrl.substring(isWindows() ? 6 : 5);

        File jar = new File(jarUrl);
        if (!jar.exists())
            throw new AssertionError("H2 jar not found in classpath: " + jar);

        copyFileToDirectory(jar, new File(getMembraneHome(), "lib"));
    }
}