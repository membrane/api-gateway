package com.predic8.membrane.core.interceptor.registration.entity;

/**
 * Created by Martin Dünkelmann(duenkelmann@predic8.de) on 18.10.17.
 */
public class User {
    private String email;
    private String password;
    private boolean confirmed;

    public User() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
