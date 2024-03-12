package com.predic8.membrane.core.security;

import com.predic8.membrane.core.exchange.*;

import java.util.*;

public interface SecurityScheme {

    void add(Exchange exchange);

     boolean hasScope(String scope);

     Set<String> getScopes();
}
