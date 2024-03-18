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

import com.predic8.membrane.core.http.Response;

public class OAuth2Exception extends Exception {
    private final String error;
    private final String errorDescription;
    private final Response response;

    public OAuth2Exception(String error, String errorDescription, Response response) {
        this.error = error;
        this.errorDescription = errorDescription;
        this.response = response;
    }

    public String getError() {
        return error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public Response getResponse() {
        return response;
    }
}
