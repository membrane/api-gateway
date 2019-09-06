/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package org.apache.http.impl.auth;

import static org.apache.http.impl.auth.NTLMEngineImpl.getType1Message;
import static org.apache.http.impl.auth.NTLMEngineImpl.getType3Message;

public class NTLMEngineTrampoline {

    public static String getResponseFor(final String message, final String username, final String password, final String host, final String domain) throws NTLMEngineException {
        final String response;
        if (message == null || message.trim().equals("")) {
            response = getType1Message(host, domain);
        } else {
            final NTLMEngineImpl.Type2Message t2m = new NTLMEngineImpl.Type2Message(message);
            response = getType3Message(username, password, host, domain, t2m.getChallenge(), t2m
                    .getFlags(), t2m.getTarget(), t2m.getTargetInfo());
        }
        return response;
    }
}
