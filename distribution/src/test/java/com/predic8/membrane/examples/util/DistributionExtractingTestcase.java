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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.test.StringAssertions.replaceInFile;
import static java.io.File.separator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.writeStringToFile;

/**
 * Extracts the .zip distribution built by Maven.
 */
public abstract class DistributionExtractingTestcase {

    private static final Logger log = LoggerFactory.getLogger(DistributionExtractingTestcase.class.getName());

    public static final String MEMBRANE_LOG_LEVEL = "info";

    public static final String LOCALHOST_2000 = "http://localhost:2000";

    public static  final String[] CONTENT_TYPE_APP_XML_HEADER = {"Content-Type", APPLICATION_XML};
    public static  final String[] CONTENT_TYPE_TEXT_XML_HEADER = {"Content-Type", TEXT_XML};
    public static  final String[] CONTENT_TYPE_APP_JSON_HEADER = {"Content-Type", APPLICATION_JSON};

    private static File unzipDir;
    private static File membraneHome;
    protected File baseDir = new File(getExampleDirName());

    protected String getExampleDirName() { return "dummy"; }

    @BeforeAll
    public static void beforeAll() throws Exception {
        log.info("unzipping router distribution");

        File targetDir = getTargetDir();

        unzipDir = getUnzipDir(targetDir);

        if (!unzipDir.exists()) {
            createDir(unzipDir);
            unzip(getZipFile(targetDir), unzipDir);
        }

        membraneHome = requireNonNull(unzipDir.listFiles((dir, name) -> name.startsWith("membrane-api-gateway")))[0];

        replaceLog4JConfig();
    }

    @AfterAll
    public static void done() {
        log.info("cleaning up...");
        recursiveDelete(unzipDir);
        log.info("cleaning up... done");
    }

    @BeforeEach
    public void init() {
        baseDir = getExampleDir(getExampleDirName());
        log.info("running test... in {}",baseDir);
    }

    private static File getTargetDir() throws IOException {
        File targetDir = new File("target").getCanonicalFile();
        if (!targetDir.exists())
            throw new RuntimeException("membraneHome " + targetDir.getName() + " does not exist.");
        return targetDir;
    }

    private static File getZipFile(File targetDir) {
        File[] files = targetDir.listFiles((dir, name) -> name.startsWith("membrane-api-gateway") && name.endsWith(".zip"));
        if (files == null || files.length != 1) {
            throw new RuntimeException("Exactly one file matching membrane-api-gateway*.zip in %s expected!".formatted(targetDir));
        }
        return files[0];
    }

    private static File getUnzipDir(File targetDir) {
        return new File(targetDir, "examples-automatic");
    }

    private static void createDir(File dir) {
        if (!dir.mkdir())
            throw new RuntimeException("Could not mkdir " + dir.getAbsolutePath());
    }

    private static void replaceLog4JConfig() throws IOException {
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

    private static void recursiveDelete(File file) {
        if (file.isDirectory())
            //noinspection ConstantConditions
            for (File child : file.listFiles())
                recursiveDelete(child);
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        return startServiceProxyScript(null, "membrane");
    }

    protected Process2 startServiceProxyScript(ConsoleWatcher watch) throws IOException, InterruptedException {
        return startServiceProxyScript(watch,"membrane");
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
