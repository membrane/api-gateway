package com.predic8.membrane.core.interceptor.jwt;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.ErrorCodeValidator;
import org.jose4j.jwt.consumer.JwtContext;

public class TidValidator implements ErrorCodeValidator {
    private static final Error MISSING_TID = new Error(-1, "No Audience (tid) claim present.");

    private final String acceptableTenantId;

    public TidValidator(String acceptableTenantId)
    {
        this.acceptableTenantId = acceptableTenantId;
    }

    @Override
    public Error validate(JwtContext jwtContext) throws MalformedClaimException
    {
        final JwtClaims jwtClaims = jwtContext.getJwtClaims();

        if (!jwtClaims.hasClaim("tid")) return null;

        String tid = jwtClaims.getClaimValue("tid", String.class);

        if (acceptableTenantId.contains(tid)) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Tenant ID (tid) claim " ).append(tid);
            sb.append(" doesn't contain an acceptable identifier.");
            sb.append(" Expected ");
            sb.append(acceptableTenantId);
            sb.append(" as an tid value.");
            return MISSING_TID;
        }
    }
}
