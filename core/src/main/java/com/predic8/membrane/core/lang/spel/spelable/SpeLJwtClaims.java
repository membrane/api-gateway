package com.predic8.membrane.core.lang.spel.spelable;

import org.jose4j.jwt.JwtClaims;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

public class SpeLJwtClaims implements SPeLablePropertyAware {

    private final JwtClaims claims;

    public SpeLJwtClaims(JwtClaims claims) {
        this.claims = claims;
    }

    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) {
        return claims.hasClaim(name);
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) {
        return new TypedValue(claims.getClaimValue(name));
    }
}
