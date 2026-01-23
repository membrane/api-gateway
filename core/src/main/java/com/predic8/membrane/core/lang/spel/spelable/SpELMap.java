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
package com.predic8.membrane.core.lang.spel.spelable;

import org.springframework.expression.*;

import java.util.*;

public class SpELMap<K, V> implements SpELLablePropertyAware, Map<K, V> {
    protected final Map<K, V> data;

    public SpELMap(Map<K, V> data) {
        this.data = data;
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) {
        if (data.containsKey(name))
            return new TypedValue(data.get(name));

        return new TypedValue(null);
    }

    @Override
    public Object getValue() {
        return data;
    }

    // ---- Map delegation ----
    @Override public int size() { return data.size(); }
    @Override public boolean isEmpty() { return data.isEmpty(); }
    @Override public boolean containsKey(Object key) { return data.containsKey(key); }
    @Override public boolean containsValue(Object value) { return data.containsValue(value); }
    @Override public V get(Object key) { return data.get(key); }
    @Override public V put(K key, V value) { return data.put(key, value); }
    @Override public V remove(Object key) { return data.remove(key); }
    @Override public void putAll(Map<? extends K, ? extends V> m) { data.putAll(m); }
    @Override public void clear() { data.clear(); }
    @Override public Set<K> keySet() { return data.keySet(); }
    @Override public Collection<V> values() { return data.values(); }
    @Override public Set<Map.Entry<K, V>> entrySet() { return data.entrySet(); }
}
