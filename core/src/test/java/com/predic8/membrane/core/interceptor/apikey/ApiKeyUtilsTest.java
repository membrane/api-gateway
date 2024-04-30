/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.core.interceptor.apikey.stores.*;
import com.predic8.membrane.core.resolver.ResolverMap;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.apikey.ApiKeyUtils.*;
import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;

public class ApiKeyUtilsTest {

    @Test
    void readFileTest() throws IOException {
        List<String> lines = readFile(getLocationPath(), new ResolverMap(), ".").toList();
        assertEquals(8, lines.size());
        assertEquals("5XF27: finance,internal", lines.get(1));
    }

    private static String getLocationPath() {
        return requireNonNull(ApiKeyFileStoreTest.class.getClassLoader().getResource("apikeys/keys.txt")).getPath();
    }
}