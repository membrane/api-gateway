/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ssl.acme;

import java.util.List;
import java.util.Map;

public class AcmeException extends Exception {
    public static final String TYPE_BAD_NONCE = "urn:ietf:params:acme:error:badNonce";

    private final String type;
    private final String detail;
    private final List<SubProblem> subproblems;
    private final String nonce;

    public AcmeException(String type, String detail, List<SubProblem> subproblems, String nonce) {
        super(type + " " + detail + (subproblems != null ? " " + subproblems : ""));
        this.type = type;
        this.detail = detail;
        this.subproblems = subproblems;
        this.nonce = nonce;
    }

    public String getType() {
        return type;
    }

    String getNonce() {
        return nonce;
    }

    public static class SubProblem {
        String type;
        String detail;
        Map identifier;

        public SubProblem(String type, String detail, Map identifier) {
            this.type = type;
            this.detail = detail;
            this.identifier = identifier;
        }
    }
}
