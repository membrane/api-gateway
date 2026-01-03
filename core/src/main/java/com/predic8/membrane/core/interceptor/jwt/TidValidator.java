/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.jwt;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.ErrorCodeValidator;
import org.jose4j.jwt.consumer.JwtContext;

public class TidValidator implements ErrorCodeValidator {
    private static final Error MISSING_TID = new Error(-1, "No Tenant ID (tid) claim present.");

    private final String acceptableTenantId;

    public TidValidator(String acceptableTenantId)
    {
        this.acceptableTenantId = acceptableTenantId;
    }

    @Override
    public Error validate(JwtContext jwtContext) throws MalformedClaimException
    {
        final JwtClaims jwtClaims = jwtContext.getJwtClaims();

        if (!jwtClaims.hasClaim("tid"))
            return MISSING_TID;

        String tid = jwtClaims.getClaimValue("tid", String.class);

        if (acceptableTenantId.equals(tid)) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Tenant ID (tid) claim '").append(tid).append("' doesn't match the expected value '");
            sb.append(acceptableTenantId).append("' .");
            return new Error(-1, sb.toString());
        }
    }
}
