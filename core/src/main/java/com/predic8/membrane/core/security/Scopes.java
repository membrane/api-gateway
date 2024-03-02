package com.predic8.membrane.core.security;

import java.util.*;

public interface Scopes {
    boolean hasScope(String scope);

    Set<String> getScopes();
}
