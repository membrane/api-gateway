package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import org.apache.commons.codec.digest.Crypt;
import java.io.*;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@MCElement(name="fileUserDataProvider")
public class FileUserDataProvider implements UserDataProvider {
    private final Map<String, User> usersByName = new HashMap<>();

    private String htpasswdPath;

    @MCAttribute
    public void setHtpasswdPath(String path) {
        this.htpasswdPath = path;
    }

    @Override
    public Map<String, String> verify(Map<String, String> postData) {
        String username = postData.get("username");
        if (username == null)
            throw new NoSuchElementException();
        User userAttributes;
        userAttributes = getUsersByName().get(username);
        if (userAttributes == null)
            throw new NoSuchElementException();
        String pw = null;
        String postDataPassword = postData.get("password");
        String userHash = userAttributes.getPassword();
        String[] userHashSplit = userHash.split("\\$");
        String salt = userHashSplit[2];
        String algo = userHashSplit[1];
        try {
            pw = createHtpasswdKeyString(algo, postDataPassword, salt);
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }
        String pw2;
        pw2 = userAttributes.getPassword();
        if (pw2 == null || !pw2.equals(pw))
            throw new NoSuchElementException();
        return userAttributes.getAttributes();
    }

    private String createHtpasswdKeyString(String algo, String password, String salt) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return Crypt.crypt(password, "$" + algo + "$" + salt);
    }

    public static class User {
        Map<String, String> attributes = new HashMap<>();

        public User(String username, String password){
            setUsername(username);
            setPassword(password);
        }

        public String getUsername() {
            return attributes.get("username");
        }

        public void setUsername(String value) {
            attributes.put("username", value);
        }

        public String getPassword() {
            return attributes.get("password");
        }

        public void setPassword(String value) {
            attributes.put("password", value);
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes.putAll(attributes);
        }
    }

    public Map<String, User> getUsersByName() {
        return usersByName;
    }

    @Override
    public void init(Router router) {
        List<String> lines = null;
        try {
            lines = Files.readAllLines(Paths.get(this.htpasswdPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String line : lines) {
            String[] parts = line.split(":");
            if (parts.length == 2) {
                User user = new User(parts[0], parts[1]);
                getUsersByName().put(user.getUsername(), user);
            }
        }
    }
}