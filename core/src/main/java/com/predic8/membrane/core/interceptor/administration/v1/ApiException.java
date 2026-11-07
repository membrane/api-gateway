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
package com.predic8.membrane.core.interceptor.administration.v1;

/**
 * Ends the handling of an admin API request with an {@code application/problem+json} response.
 */
class ApiException extends RuntimeException {

    private final int status;
    private final String title;

    ApiException(int status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    static ApiException badRequest(String detail) {
        return new ApiException(400, "Bad request.", detail);
    }

    static ApiException forbidden(String detail) {
        return new ApiException(403, "Forbidden.", detail);
    }

    static ApiException notFound(String detail) {
        return new ApiException(404, "Not found.", detail);
    }

    static ApiException notImplemented(String detail) {
        return new ApiException(501, "Not implemented.", detail);
    }

    int getStatus() {
        return status;
    }

    String getTitle() {
        return title;
    }
}
