/*
 *  Copyright 2017 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.openapi.model;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.model.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.Utils.inputStreamToString;

public class InputStreamBody implements Body {

    private final InputStream is;
    private JsonNode node;

    public InputStreamBody(InputStream is) {
        this.is = is;
    }

    public InputStream getInputStream() {
        return is;
    }

    @Override
    public String asString() throws IOException {
        return inputStreamToString(is);
    }

    @Override
    public JsonNode getJson() throws IOException {
        if (node != null)
            return node;

        node = om.readValue(is, JsonNode.class);
        return node;
    }


}
