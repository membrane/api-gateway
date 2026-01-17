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

package com.predic8.membrane.core.interceptor.headerfilter;

import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterRule.*;
import static org.junit.jupiter.api.Assertions.*;

class HeaderFilterTest {

    @Test
    void removeAllUnknown() {

        var header = new Header();
        header.add("X-Foo", "foo");
        header.add("X-Bar", "bar");
        header.add("X-Baz", "baz");

        var hf = new HeaderFilter();

        hf.setRules(List.of(
                keep("X-Foo"),
                keep("X-Bar"),
                remove(".*")));

        hf.filter(header);

        var fields = header.getFields();
        assertEquals(2, fields.size());
        assertEquals(List.of("X-Foo", "X-Bar"), headerNames(header));
    }

    @Test
    void repeatedFields() {

        var header = new Header();
        header.add("X-Foo", "foo");
        header.add("X-Bar", "bar");
        header.add("X-Bar", "bar");
        header.add("X-Bar", "bar");
        header.add("X-Baz", "baz");

        var hf = new HeaderFilter();

        hf.setRules(List.of(
                keep("X-Foo"),
                keep("X-Bar"),
                remove(".*")));

        hf.filter(header);

        var fields = header.getFields();
        assertEquals(4, fields.size());
        assertEquals(List.of("X-Foo", "X-Bar","X-Bar","X-Bar"), headerNames(header));
    }

    private List<String> headerNames(Header header) {
        return header.getFields().stream().map(hf -> hf.getHeaderName().getName()).toList();
    }

}