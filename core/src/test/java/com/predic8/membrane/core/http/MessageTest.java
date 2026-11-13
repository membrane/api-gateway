/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.http;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void isContentEncodingDecodedOnlyForTheCodingsThatAreDecoded() throws Exception {
        for (final var coding : new String[]{"gzip", "deflate", "br", "zstd", "GZIP", "ZSTD"})
            assertTrue(requestWithContentEncoding(coding).isContentEncodingDecoded(), coding);
        for (final var coding : new String[]{"unknown", "identity", "gzip, br"})
            assertFalse(requestWithContentEncoding(coding).isContentEncodingDecoded(), coding);
        assertFalse(Request.post("/").body("plain").build().isContentEncodingDecoded());
    }

    private static Message requestWithContentEncoding(String coding) throws URISyntaxException {
        return Request.post("/").body(new byte[]{1}).header("Content-Encoding", coding).build();
    }
}
