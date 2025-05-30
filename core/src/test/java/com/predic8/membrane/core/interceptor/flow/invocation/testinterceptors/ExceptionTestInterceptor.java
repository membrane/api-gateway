/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.flow.invocation.testinterceptors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;

import java.io.*;

import static com.predic8.membrane.core.http.Response.*;

public class ExceptionTestInterceptor extends AbstractInterceptor {

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            exc.setResponse(ok().body(exc.getRequest().getBody().getContent()).build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException();
    }

    @Override
    public String getDisplayName() {
        return "ExceptionTestInterceptor";
    }
}
