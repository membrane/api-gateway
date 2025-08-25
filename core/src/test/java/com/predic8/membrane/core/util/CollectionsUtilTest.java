/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.util.CollectionsUtil.*;
import static java.util.Collections.emptyIterator;
import static org.junit.jupiter.api.Assertions.*;

public class CollectionsUtilTest {

    @Test
    void normal() {
        assertEquals(List.of(1,2,3), CollectionsUtil.concat(List.of(1),List.of(2,3)));
    }

    @Test
    void l1Null() {
        assertEquals(List.of(2,3), CollectionsUtil.concat(null,List.of(2,3)));
    }

    @Test
    void l2Null() {
        assertEquals(List.of(1), CollectionsUtil.concat(List.of(1),null));
    }

    @Test
    void allNull() {
        assertEquals(List.of(), CollectionsUtil.concat(null,null));
    }

    @Test
    void toList() {
        assertIterableEquals(List.of(1,2,3), CollectionsUtil.toList(List.of(1,2,3).iterator()));
    }

    @Test
    void count_iterator() {
        assertEquals(0, count(emptyIterator()));
        assertEquals(3, count(List.of("a", "b", "c").iterator()));
    }
}