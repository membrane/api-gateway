package com.predic8.membrane.core.interceptor.authentication.session;

import java.util.*;

public abstract class AbstractUserDataProvider implements UserDataProvider {

    protected Map<String, String> filterPassword(Map<String, String> userAttributes) {
        userAttributes.remove("password");
        return userAttributes;
    }

}
