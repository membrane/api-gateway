/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.resolver;

import com.predic8.membrane.core.util.functionalInterfaces.*;

import java.io.*;
import java.util.*;

public class StaticStringResolver implements Resolver {

    /**
     * Returns the parameter back as InputStream. Useful for tests.
     * @param schema String with content
     * @return InputStream of the provided String
     */
    @Override
    public InputStream resolve(String schema) {
        return new ByteArrayInputStream(schema.getBytes());
    }

    @Override
    public void observeChange(String url, ExceptionThrowingConsumer<InputStream> consumer) {

    }

    @Override
    public List<String> getChildren(String url) {
        return List.of();
    }

    @Override
    public long getTimestamp(String url) {
        return 0;
    }
}
