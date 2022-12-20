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

import java.util.*;

public class MapUtils {

    public static Map<String,String> stringMap(String k1,String v1) {
        Map<String,String> m = new HashMap<>();
        m.put(k1,v1);
        return m;
    }

    public static Map<String,String> stringMap(String k1,String v1, String k2,String v2) {
        Map<String,String> m = stringMap(k1,v1);
        m.put(k2,v2);
        return m;
    }
}
