/* Copyright 2023, 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.test;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.OSUtil;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.predic8.membrane.core.util.OSUtil.isWindows;
import static com.predic8.membrane.core.util.URIUtil.*;
import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestUtil {

    private static final String WINDOWS_DRIVE = detectWindowsDrive();

    public static InputStream getResourceAsStream(Object thisObj, String filename) {
        return thisObj.getClass().getClassLoader().getResourceAsStream(filename);
    }
    public static byte[] readResource(String path) throws IOException {
        // Make sure stream is closed
        try (InputStream is = requireNonNull(TestUtil.class.getResourceAsStream(path),
                "Missing test resource: " + path)) {
            return is.readAllBytes();
        }
    }

    public static Exchange assembleExchange(String hostHeader, String method, String path, String proxyVersion, int port, String localIp) throws UnknownHostException {
        return new Exchange(new FakeHttpHandler(port, Inet4Address.getByName(localIp))) {{
            setRequest(new Request() {{
                getHeader().setHost(hostHeader);
                setMethod(method);
                setUri(path);
                setVersion(proxyVersion);
            }});
        }};
    }

    @Test
    void get_PathFromResource() {
        assertTrue(new File(getPathFromResource("log4j2.xml")).exists());
    }

    public static String getPathFromResource(String resourcePaht) {
        return pathFromFileURI(OpenAPITestUtils.class.getResource("../../../../../..").getPath() + resourcePaht);
    }

    public static String wl(String windows, String linux) {
        if (isWindows())
            return normalizeWindowsDrive(windows);
        return linux;
    }

    // Determines the current Windows drive (e.g., "C:") from the process working directory root
    // falls back to "C:" on non-Windows or on errors.
    // Example: if running from "D:\\work\\proj", returns "D:".
    private static String detectWindowsDrive() {
        if (!isWindows()) return "C:";

        try {
            Path root = Paths.get("").toAbsolutePath().getRoot();
            if (root == null) return "C:";
            String r = root.toString();
            return (r.length() >= 2 && r.charAt(1) == ':') ? r.substring(0, 2) : "C:";
        } catch (Exception ignored) {
            return "C:";
        }
    }

    private static String normalizeWindowsDrive(String s) {
        if (!isWindows() || s == null || "C:".equals(WINDOWS_DRIVE)) return s;

        if (s.startsWith("file:/C:")) {
            return "file:/" + WINDOWS_DRIVE + s.substring("file:/C:".length());
        }
        if (s.startsWith("C:\\")) {
            return WINDOWS_DRIVE + s.substring(2);
        }
        if (s.startsWith("C:/")) {
            return WINDOWS_DRIVE + s.substring(2);
        }
        return s;
    }

}
