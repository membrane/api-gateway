package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.annot.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.authentication.SecurityUtils.*;

public abstract class AbstractUserDataProvider implements UserDataProvider {

    protected Map<String, User> usersByName = new HashMap<>();

    @Override
    public Map<String, String> verify(Map<String, String> postData) {
        var username = postData.get("username");
        if (username == null) throw new NoSuchElementException();

        var userAttributes = getUsersByName().get(username);
        if (userAttributes == null) throw new NoSuchElementException();

        verifyLoginOrThrow(postData, userAttributes.getPassword());
        return userAttributes.getAttributes();
    }

    public Map<String, User> getUsersByName() {
        return usersByName;
    }
}
