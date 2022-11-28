package com.predic8.membrane.core.util;

/**
 * This exception will be caught by the Starter and only the exception message will be printed. Useful
 * give out information to the user about missconfiguration.
 */
public class UserInformationException extends RuntimeException{
    public UserInformationException(String e) {
        super(e);
    }
}
