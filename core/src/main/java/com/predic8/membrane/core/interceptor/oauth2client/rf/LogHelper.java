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
package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogHelper {
    private static final Logger log = LoggerFactory.getLogger(LogHelper.class);
    private LogInterceptor logi;

    public LogHelper() {
        if (log.isDebugEnabled()) {
            logi = new LogInterceptor();
            logi.setHeaderOnly(false);
        }
    }

    public void handleRequest(Exchange e) throws Exception {
        if (log.isDebugEnabled()) {
            logi.handleRequest(e);
        }
    }

    public void handleResponse(Exchange e) throws Exception {
        if (log.isDebugEnabled()) {
            logi.handleResponse(e);
        }
    }

}
