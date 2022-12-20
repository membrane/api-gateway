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

public class ListUtils {

    public static List<String> stringList(String s1) {
        List<String> list = new ArrayList<>();
        list.add(s1);
        return list;
    }

    public static List<String> stringList(String s1, String s2) {
        List<String> list = stringList(s1);
        list.add(s2);
        return list;
    }

    public static List<String> stringList(String s1, String s2, String s3) {
        List<String> list = stringList(s1,s2);
        list.add(s3);
        return list;
    }
}
