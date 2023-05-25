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

package com.predic8.membrane.examples.util;

import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.test.AssertUtils.*;
import static java.io.File.*;
import static java.lang.Thread.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;
import static org.apache.commons.io.FileUtils.*;

/**
 * Extracts the .zip distribution built by Maven.
 */
public abstract class DistributionExtractingTestcase {

    public static final String MEMBRANE_LOG_LEVEL = "debug";

    public static final String BLZ_SERVICE = "http://localhost:2000/bank/services/BLZService";
    public static final String BLZ_SERVICE_WSDL = BLZ_SERVICE + "?wsdl";

    public static final String LOCALHOST_2000 = "http://localhost:2000";

    public static  final String[] CONTENT_TYPE_APP_XML_HEADER = {"Content-Type", APPLICATION_XML};
    public static  final String[] CONTENT_TYPE_TEXT_XML_HEADER = {"Content-Type", TEXT_XML};
    public static  final String[] CONTENT_TYPE_APP_JSON_HEADER = {"Content-Type", APPLICATION_JSON};

    public static  final String[] CONTENT_TYPE_SOAP_HEADER = {"Content-Type", APPLICATION_SOAP};

    private File unzipDir;
    private File membraneHome;
    protected File baseDir = new File(getExampleDirName());

    protected String getExampleDirName() { return "dummy"; }

    @BeforeEach
    public void init() throws IOException, InterruptedException {
        System.out.println("unzipping router distribution [" + getClass().getSimpleName() + "]...");

        File targetDir = getTargetDir();

        unzipDir = getUnzipDir(targetDir);

        unzip(getZipFile(targetDir), unzipDir);

        membraneHome = requireNonNull(unzipDir.listFiles((dir, name) -> name.startsWith("membrane-api-gateway")))[0];
        baseDir = getExampleDir(getExampleDirName());

        replaceLog4JConfig();

        System.out.println("running test...");
    }

    private File getTargetDir() throws IOException {
        File targetDir = new File("target").getCanonicalFile();
        if (!targetDir.exists())
            throw new RuntimeException("membraneHome " + targetDir.getName() + " does not exist.");
        return targetDir;
    }

    private File getZipFile(File targetDir) {
        File zip = null;
        {
            File[] files = targetDir.listFiles((dir, name) -> name.startsWith("membrane-api-gateway") && name.endsWith(".zip"));
            if (files == null) {
                throw new RuntimeException("Could not find zip file!");
            }
            if (files.length > 1)
                throw new RuntimeException("found more than one membrane-api-gateway*.zip");
            if (files.length == 1)
                zip = files[0];
        }
        if (zip == null)
            throw new RuntimeException("TODO: calling 'ant dist-router' automatically is not implemented.");
        return zip;
    }

    private File getUnzipDir(File targetDir) throws InterruptedException {
        File dir = new File(targetDir, "examples-automatic");
        if (dir.exists()) {
            recursiveDelete(dir);
            sleep(300);
        }
        if (!dir.mkdir())
            throw new RuntimeException("Could not mkdir " + dir.getAbsolutePath());
        return dir;
    }

    private void replaceLog4JConfig() throws IOException {
        File log4jproperties = new File(membraneHome, "conf" + separator + "log4j2.xml");
        if (!log4jproperties.exists())
            throw new RuntimeException("log4j2.xml does not exits.");

        var log4j2xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Configuration>
                    <Appenders>
                        <Console name="STDOUT" target="SYSTEM_OUT">
                            <PatternLayout pattern="%s" />
                        </Console>
                    </Appenders>
                    <Loggers>
                        <Logger name="com.predic8" level="%s" />
                        <Root level="warn">
                            <AppenderRef ref="STDOUT" />
                        </Root>
                    </Loggers>
                </Configuration>
                """.formatted("%d{ABSOLUTE} %5p %c{1}:%L - %m%n", MEMBRANE_LOG_LEVEL);
        writeStringToFile(
                log4jproperties,
                log4j2xml, UTF_8);
    }

    public File getExampleDir(String name) {
        File exampleDir = new File(membraneHome, "examples" + separator + name);
        if (!exampleDir.exists())
            throw new RuntimeException("Example dir " + exampleDir.getAbsolutePath() + " does not exist.");
        return exampleDir;
    }

    public File getMembraneHome() {
        return membraneHome;
    }

    @AfterEach
    public void done() {
        System.out.println("cleaning up...");
        recursiveDelete(unzipDir);
        System.out.println("done.");
    }

    private void recursiveDelete(File file) {
        if (file.isDirectory())
            //noinspection ConstantConditions
            for (File child : file.listFiles())
                recursiveDelete(child);
        if (!file.delete())
            throw new RuntimeException("could not delete " + file.getAbsolutePath());
    }

    public static void unzip(File zip, File target) throws IOException {
        ZipFile zipFile = new ZipFile(zip);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                // Assume directories are stored parents first then children.
                // This is not robust, just for demonstration purposes.
                //noinspection ResultOfMethodCallIgnored
                new File(target, entry.getName()).mkdir();
            } else {
                final File zipEntryFile = new File(target, entry.getName());
                if (!zipEntryFile.toPath().normalize().startsWith(target.toPath().normalize())) {
                    throw new IOException("Bad zip entry");
                }
                try (FileOutputStream fos = new FileOutputStream(zipEntryFile)) {
                    copyInputStream(zipFile.getInputStream(entry),
                            new BufferedOutputStream(fos));
                }
            }
        }
        zipFile.close();
    }

    public static void copyInputStream(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0)
            out.write(buffer, 0, len);

        in.close();
        out.close();
    }

    protected String readFileFromBaseDir(String filename) throws IOException {
        return readFileToString(new File(baseDir,filename), UTF_8);
    }

    protected Process2 startServiceProxyScript() throws IOException, InterruptedException {
        return startServiceProxyScript(null, "service-proxy");
    }

    protected Process2 startServiceProxyScript(ConsoleWatcher watch) throws IOException, InterruptedException {
        return startServiceProxyScript(watch,"service-proxy");
    }

    protected Process2 startServiceProxyScript(ConsoleWatcher watch, String script) throws IOException, InterruptedException {
        Process2.Builder builder = new Process2.Builder().in(baseDir);
        if (watch != null)
            builder = builder.withWatcher(watch);
        return builder.script(script).waitForMembrane().start();
    }

    protected String readFile(String s) throws IOException {
        return readFileToString(new File(baseDir, s), UTF_8);
    }

    /**
     * Replace String a with b in file
     * @param a String to be replaced
     * @param b String that replaces
     * @throws IOException Problem accessing File
     */
    protected void replaceInFile2(String filename, String a, String b) throws IOException {
        replaceInFile(new File(baseDir, filename), a, b);
    }

    public InputStream getResourceAsStream(String filename) {
        return getClass().getClassLoader().getResourceAsStream(filename);
    }
}
