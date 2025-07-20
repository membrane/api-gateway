/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import java.util.*;

public class InterceptorUtil {

    public static  <T extends Interceptor> List<T> getInterceptors(List<Interceptor> interceptors, Class<T> clazz) {
        return interceptors.stream().filter(i -> i.getClass().equals(clazz))
                .map(i -> clazz.cast(i))
                .toList();
    }

    public static <T extends Interceptor> Optional<T> getFirstInterceptorOfType(List<Interceptor> interceptors, Class<T> type) {
        return getInterceptors(interceptors, type).stream().findFirst();
    }

}

