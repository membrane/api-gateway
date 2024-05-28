/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RequestBuilderTest {

    @Test
    public void testSetContentType(){
        Request req = new Request.Builder().contentType("ContentType").build();

        assertEquals("ContentType",req.getHeader().getContentType());
    }

    @Test
    void authorization() {
        Request req = new Request.Builder().authorization("alice","secret").build();
        assertEquals("Basic YWxpY2U6c2VjcmV0",req.getHeader().getAuthorization());
    }

    @Test
    void multiSameHeader() {
        Request req = new Request.Builder()
                .header("X-Test", "31415")
                .header("X-Test", "27182").build();
        assertEquals("""
                X-Test: 31415\r
                X-Test: 27182\r
                """, req.getHeader().toString());
    }
}
