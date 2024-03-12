package com.predic8.membrane.core.security;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.Request.*;

public class ApiKeySecurityScheme extends AbstractSecurityScheme {

    /**
     * Name of the header, query or cookie parameter
     */
   public String name;

   public In in;

    /**
     *
     * @param in Location of the key
     * @param name Name of the header, query or cookie parameter
     */
    public ApiKeySecurityScheme(In in, String name) {
        this.name = name;
        this.in = in;
    }

    @Override
    public String toString() {
        return "ApiKeySecurityScheme{" +
               "name='" + name + '\'' +
               '}';
    }
}
