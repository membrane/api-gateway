package com.predic8.membrane.core.interceptor.authentication.session;

import java.util.*;

import static com.predic8.membrane.core.interceptor.authentication.SecurityUtils.*;

public abstract class AbstractUserDataProvider implements UserDataProvider {

    protected Map<String, User> usersByName = new HashMap<>();

    @Override
    public Map<String, String> verify(Map<String, String> postData) {
        String username = postData.get("username");
        if (username == null) throw new NoSuchElementException();

        User userAttributes = getUsersByName().get(username);
        if (userAttributes == null) throw new NoSuchElementException();

        verifyLoginOrThrow(postData, userAttributes.getPassword());
        return filterPassword( userAttributes.getAttributes());
    }

    /**
     * Take password out course the user attributes are passed to the next interceptor.
     * @param userAttributes
     * @return
     */
    protected Map<String, String> filterPassword(Map<String, String> userAttributes) {
        userAttributes.remove("password");
        return userAttributes;
    }

    public Map<String, User> getUsersByName() {
        return usersByName;
    }
}
