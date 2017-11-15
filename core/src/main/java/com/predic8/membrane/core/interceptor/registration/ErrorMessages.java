/* Copyright 2017 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.registration;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;

/**
 * Created by Martin Dünkelmann(duenkelmann@predic8.de) on 20.10.17.
 */
class ErrorMessages {
    static Outcome returnErrorUserAlreadyExists(Exchange exc) {
        exc.setResponse(Response.badRequest("User already exists.").build());
        return Outcome.RETURN;
    }

    static Outcome returnErrorBadRequest(Exchange exc) {
        exc.setResponse(Response.badRequest("POST-Request with the following body is needed for account registration:\t {\"email\":\"E-Mail\",\"password\":\"Password\"}").build());
        return Outcome.RETURN;
    }
}
