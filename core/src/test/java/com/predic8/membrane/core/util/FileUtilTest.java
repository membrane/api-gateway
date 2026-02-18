/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.nio.file.Path;

import static com.predic8.membrane.core.util.FileUtil.*;
import static com.predic8.membrane.core.util.OSUtil.wl;
import static java.io.File.separator;
import static org.junit.jupiter.api.Assertions.*;

public class FileUtilTest {

    InputStream is;

    @BeforeEach
    public void setUp() {
        is = new ByteArrayInputStream("Hello".getBytes());
    }

    @Test
    public void read() {
        assertEquals("Hello", readInputStream(is));
    }

    @Test
    void writeInputStreamToFileTest() throws IOException {
        writeInputStreamToFile(getTmpFilename(), is);
        assertEquals("Hello", readInputStream(new FileInputStream(getTmpFilename())));
    }

    private String getTmpFilename() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        return Path.of(tmpDir, "test.tmp").toString();
    }

    @Test
    void resolveFile() throws URISyntaxException {
        var file = new File("/a/b/c");
        var dir = new File("/a/b/c/");
        assertEquals(wl("file:/C:/a/b/c/d",  "file:/a/b/c/d"),  FileUtil.resolve(file, "d"));
        assertEquals(wl("file:/C:/a/b/c/d/", "file:/a/b/c/d/"), FileUtil.resolve(file, "d/"));
        assertEquals(wl("file:/C:/a/b/c/d/", "file:/a/b/c/d/"), FileUtil.resolve(dir,  "d/"));
    }

    @Test
    void directoryPart() {
        assertNull(getDirectoryPart(""));
        assertEquals(new File(separator), FileUtil.getDirectoryPart("/"));
        assertEquals(new File("\\"), FileUtil.getDirectoryPart("\\"));
        assertEquals(new File(separator), FileUtil.getDirectoryPart("/foo"));
        assertEquals(new File("/foo/"), FileUtil.getDirectoryPart("/foo/"));
        assertEquals(new File("/foo/"), FileUtil.getDirectoryPart("/foo/bar"));
        assertEquals(new File("/foo/"), FileUtil.getDirectoryPart("/foo/bar.txt"));
    }
}