/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.http.*;

public abstract class AbstractMessageValidator implements MessageValidator {

    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    public static final String UNKNOWN = "unknown";

    protected String getSourceOfError(Message msg) {
        if (msg instanceof Request)
            return REQUEST;
        if (msg instanceof Response)
            return RESPONSE;
        return UNKNOWN;
    }

    @Override
    public void init() {

    }
}
