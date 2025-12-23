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
