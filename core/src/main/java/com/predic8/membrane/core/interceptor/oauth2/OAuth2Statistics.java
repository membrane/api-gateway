/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2;

public class OAuth2Statistics {

    private int successfulRequests;
    private int createdAccessTokens;
    private int validatedAccessTokens;
    private int invalidAccessTokens;
    private int codeFlow;
    private int implicitFlow;
    private int passwordFlow;
    private int clientCredentialsFlow;


    private int refreshTokenFlow;
    private StringBuilder builder = new StringBuilder();

    private String newLine = "<br/>";

    @Override
    public synchronized String toString() {
        builder.setLength(0);
        builder.append(newLine);
        writeLine(successfulRequests,"Successful requests");
        writeLine(createdAccessTokens,"Created access tokens");
        writeLine(validatedAccessTokens,"Valid access tokens");
        writeLine(invalidAccessTokens, "Invalid access tokens");
        writeLine(codeFlow,"Code flow requests");
        writeLine(implicitFlow,"Implicit flow requests");
        writeLine(passwordFlow,"Password flow requests");
        writeLine(clientCredentialsFlow,"Client credentials flow requests");
        writeLine(refreshTokenFlow,"Refresh token requests");
        return builder.toString().substring(0,builder.length() - newLine.length());
    }

    private StringBuilder writeLine(int value, String text){
        return builder.append(value > 0 ? text +": " + value : "").append(value > 0 ? newLine : "");
    }

    public synchronized void successfulRequest(){
        successfulRequests++;
    }

    public synchronized void accessTokenCreated(){
        createdAccessTokens++;
    }

    public synchronized void accessTokenValid(){
        validatedAccessTokens++;
    }

    public synchronized void accessTokenInvalid(){
        invalidAccessTokens++;
    }

    public synchronized void codeFlow(){
        codeFlow++;
    }

    public synchronized void implicitFlow(){
        implicitFlow++;
    }

    public synchronized void passwordFlow(){
        passwordFlow++;
    }

    public synchronized void clientCredentialsFlow(){
        clientCredentialsFlow++;
    }

    public synchronized void refreshTokenFlow() {
        refreshTokenFlow++;
    }

    public int getSuccessfulRequests() {
        return successfulRequests;
    }

    public void setSuccessfulRequests(int successfulRequests) {
        this.successfulRequests = successfulRequests;
    }

    public int getCreatedAccessTokens() {
        return createdAccessTokens;
    }

    public void setCreatedAccessTokens(int createdAccessTokens) {
        this.createdAccessTokens = createdAccessTokens;
    }

    public int getValidatedAccessTokens() {
        return validatedAccessTokens;
    }

    public void setValidatedAccessTokens(int validatedAccessTokens) {
        this.validatedAccessTokens = validatedAccessTokens;
    }

    public int getInvalidAccessTokens() {
        return invalidAccessTokens;
    }

    public void setInvalidAccessTokens(int invalidAccessTokens) {
        this.invalidAccessTokens = invalidAccessTokens;
    }

    public int getCodeFlow() {
        return codeFlow;
    }

    public void setCodeFlow(int codeFlow) {
        this.codeFlow = codeFlow;
    }

    public int getImplicitFlow() {
        return implicitFlow;
    }

    public void setImplicitFlow(int implicitFlow) {
        this.implicitFlow = implicitFlow;
    }

    public int getPasswordFlow() {
        return passwordFlow;
    }

    public void setPasswordFlow(int passwordFlow) {
        this.passwordFlow = passwordFlow;
    }

    public int getClientCredentialsFlow() {
        return clientCredentialsFlow;
    }

    public void setClientCredentialsFlow(int clientCredentialsFlow) {
        this.clientCredentialsFlow = clientCredentialsFlow;
    }

    public int getRefreshTokenFlow() {
        return refreshTokenFlow;
    }

    public void setRefreshTokenFlow(int refreshTokenFlow) {
        this.refreshTokenFlow = refreshTokenFlow;
    }
}
