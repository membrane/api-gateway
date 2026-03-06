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

import com.predic8.membrane.core.http.cookie.Cookies;
import com.predic8.membrane.core.http.cookie.MimeHeaders;

import java.util.*;

public class LazyCookieMap implements Map<String, String> {

    private final Header header;
    private Map<String, String> delegate;

    public LazyCookieMap(Header header) {
        this.header = Objects.requireNonNull(header);
    }

    private Map<String, String> map() {
        if (delegate == null) {
            delegate = parseCookies(header);
        }
        return delegate;
    }

    private static Map<String, String> parseCookies(Header header) {
        var parsed = new LinkedHashMap<String, String>();
        var cookies = new Cookies(new MimeHeaders(header));
        for (int i = 0; i < cookies.getCookieCount(); i++) {
            var cookie = cookies.getCookie(i);
            parsed.putIfAbsent(cookie.getName().toString(), cookie.getValue().toString());
        }
        return parsed;
    }

    @Override
    public int size() {
        return map().size();
    }

    @Override
    public boolean isEmpty() {
        return map().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map().containsValue(value);
    }

    @Override
    public String get(Object key) {
        return map().get(key);
    }

    @Override
    public String put(String key, String value) {
        return map().put(key, value);
    }

    @Override
    public String remove(Object key) {
        return map().remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        map().putAll(m);
    }

    @Override
    public void clear() {
        map().clear();
    }

    @Override
    public Set<String> keySet() {
        return map().keySet();
    }

    @Override
    public Collection<String> values() {
        return map().values();
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return map().entrySet();
    }

    @Override
    public String toString() {
        return map().toString();
    }
}
