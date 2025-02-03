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
package com.predic8.membrane.core.lang.groovy;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.lang.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

public class PathParametersMap implements Map<String, String> {

    private static final Logger log = LoggerFactory.getLogger(PathParametersMap.class);

    protected final Exchange exchange;
    protected Map<String , String> data;

    public PathParametersMap(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public int size() {
        load();
        return data.size();
    }

    @Override
    public boolean isEmpty() {
        load();
        return data.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        load();
        return data.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        load();
        return data.containsKey(value);
    }

    @Override
    public String get(Object key) {
        load();
        return data.get(key);
    }

    @Override
    public @Nullable String put(String key, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ? extends String> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Set<String> keySet() {
        return data.keySet();
    }

    @Override
    public @NotNull Collection<String> values() {
        return data.values();
    }

    @Override
    public @NotNull Set<Entry<String, String>> entrySet() {
        return data.entrySet();
    }

    private void load() {
        if (data == null) {
            data = ScriptingUtils.extractPathParameters(exchange);
        }
    }
}
