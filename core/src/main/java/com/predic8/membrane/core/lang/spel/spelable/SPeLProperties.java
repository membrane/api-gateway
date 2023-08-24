package com.predic8.membrane.core.lang.spel.spelable;

import org.jose4j.jwt.JwtClaims;

import java.util.Map;

public class SPeLProperties extends SPelMap<String, Object> {

    public SPeLProperties(Map<String, Object> properties) {
        super(properties);

        this.data.computeIfPresent("jwt", (s, o) -> {
            if (o instanceof JwtClaims claims) {
                return new SpeLJwtClaims(claims);
            }
            return o;
        });
    }

}
