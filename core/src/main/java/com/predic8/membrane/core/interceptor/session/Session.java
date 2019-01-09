package com.predic8.membrane.core.interceptor.session;

import java.util.*;
import java.util.stream.Collectors;

public class Session {

    public enum AuthorizationLevel{
        ANONYMOUS,
        VERIFIED
    }

    protected final static String INTERNAL_PREFIX = "_internal_";
    protected final static String AUTHORIZATION_LEVEL = "auth_level";

    private String usernameKeyName;
    Map<String, Object> content;

    boolean isDirty;

    public Session(String usernameKeyName, Map<String, Object> content) {
        this.usernameKeyName = usernameKeyName;
        this.content = content;
        if(getInternal(AUTHORIZATION_LEVEL) == null)
            setAuthorization(AuthorizationLevel.ANONYMOUS);
        isDirty = false;
    }

    public Session(Map<String, Object> content) {
        this("username",content);
    }

    public <T> T get(String key) {
        return (T) getMultiple(key).get(key);
    }

    public <T> void put(String key, T value) {
        put(Collections.singletonMap(key, value));
    }

    public Map<String, Object> getMultiple(String... keys) {
        Set<String> keysUnique = new HashSet<>(Arrays.asList(keys));
        return content
                .entrySet()
                .stream()
                .filter(entry -> keysUnique.contains(entry.getKey()))
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
    }

    public void remove(String... keys) {
        Set<String> keysUnique = new HashSet<>(Arrays.asList(keys));
        content = content
                .entrySet()
                .stream()
                .filter(entry -> !keysUnique.contains(entry.getKey()))
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
        dirty();
    }

    public void put(Map<String, Object> map) {
        content.putAll(map);
        dirty();
    }

    private void dirty() {
        isDirty = true;
    }

    public Map<String, Object> get() {
        return content;
    }

    public void clear() {
        content.clear();
    }

    public void setUsername(String username){
        put(usernameKeyName,username);
    }

    public <T> T getUsername(){
        return get(usernameKeyName);
    }

    public void authorize(String username){
        setUsername(username);
        setAuthorization(AuthorizationLevel.VERIFIED);
    }

    protected void setAuthorization(AuthorizationLevel level) {
        putInternal(AUTHORIZATION_LEVEL, level.name());
    }

    protected AuthorizationLevel getAuthorization(){
        try {
            return AuthorizationLevel.valueOf(getInternal(AUTHORIZATION_LEVEL));
        }catch (IllegalArgumentException e){
            // e.g. No enum constant com.predic8.membrane.core.interceptor.session.Session.AuthorizationLevel.ANONYMOUS,ANONYMOUS
            setAuthorization(AuthorizationLevel.ANONYMOUS);
            return AuthorizationLevel.ANONYMOUS;
        }
    }

    public boolean isVerified(){
        return getAuthorization() == AuthorizationLevel.VERIFIED && getUsername() != null;
    }

    public void clearAuthentication(){
        removeInternal(usernameKeyName);
        removeInternal(AUTHORIZATION_LEVEL);
    }

    protected String internalKey(String key){
        return INTERNAL_PREFIX+key;
    }

    protected <T> void putInternal(String key, T value){
        put(internalKey(key),value);
    }

    protected <T> T getInternal(String key){
        return get(internalKey(key));
    }

    protected void removeInternal(String key) {
        remove(internalKey(key));
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }
}
