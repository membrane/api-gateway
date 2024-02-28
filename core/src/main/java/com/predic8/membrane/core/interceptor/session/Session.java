/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.interceptor.oauth2.ParamNames.ACCESS_TOKEN;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.OAUTH2_ANSWER;

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

    public Session(){};

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
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void remove(String... keys) {
        Set<String> keysUnique = new HashSet<>(Arrays.asList(keys));
        content = content
                .entrySet()
                .stream()
                .filter(entry -> !keysUnique.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

    @JsonIgnore
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
        }catch (IllegalArgumentException | NullPointerException e){
            // e.g. No enum constant com.predic8.membrane.core.interceptor.session.Session.AuthorizationLevel.ANONYMOUS,ANONYMOUS
            setAuthorization(AuthorizationLevel.ANONYMOUS);
            return AuthorizationLevel.ANONYMOUS;
        }
    }

    @JsonIgnore
    public Object getOAuth2Answer() {
        return get(OAUTH2_ANSWER);
    }

    public void setOAuth2Answer(String answer) {
        put(OAUTH2_ANSWER, answer);
    }

    @JsonIgnore
    public OAuth2AnswerParameters getOAuth2AnswerParameters() {
        try {
            return OAuth2AnswerParameters.deserialize(get(OAUTH2_ANSWER));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasOAuth2Answer() {
        return getOAuth2Answer() != null;
    }

    @JsonIgnore
    public String getAccessToken() {
        return get(ACCESS_TOKEN);
    }

    @JsonIgnore
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

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public String getUsernameKeyName() {
        return usernameKeyName;
    }

    public void setUsernameKeyName(String usernameKeyName) {
        this.usernameKeyName = usernameKeyName;
    }
}
