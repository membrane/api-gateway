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

package com.predic8.membrane.core.interceptor.oauth2.parameter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.interceptor.oauth2.BufferedJsonGenerator;

import java.io.IOException;
import java.util.*;

public class ClaimsParameter {
    public static final String USERINFO = "userinfo";
    public static final String ID_TOKEN = "id_token";

    private final Set<String> supportedClaims;
    private Map<String,Object> cleanedJson;

    /**
     * @param supportedClaims is the list of claims that are specified in a ClaimsList object
     * @param claimsParameter is a parameter value from the request
     */
    public ClaimsParameter(Set<String> supportedClaims, String claimsParameter){
        this.supportedClaims = supportedClaims;
        if(claimsParameter != null && !claimsParameter.isEmpty())
            parseClaimsParameter(claimsParameter);
    }

    public static String writeCompleteJson(String userinfoClaims, String idTokenClaims) throws IOException {
        String[] userinfo = null;
        if(userinfoClaims != null && !userinfoClaims.isEmpty())
            userinfo = userinfoClaims.split(" ");
        String[] idToken = null;
        if(idTokenClaims != null && !idTokenClaims.isEmpty())
            idToken = idTokenClaims.split(" ");
        return writeCompleteJson(userinfo, idToken);
    }

    public static String writeCompleteJson(String[] userinfoClaims, String[] idTokenClaims) throws IOException {
        if(userinfoClaims == null && idTokenClaims == null)
            return "";
        try (var bufferedJsonGenerator = new BufferedJsonGenerator()) {
            var gen = bufferedJsonGenerator.getJsonGenerator();
            gen.writeStartObject();
            if (userinfoClaims != null)
                writeSingleClaimsObject(gen, USERINFO, userinfoClaims);
            if (idTokenClaims != null)
                writeSingleClaimsObject(gen, ID_TOKEN, idTokenClaims);
            gen.writeEndObject();
            return bufferedJsonGenerator.getJson();
        }
    }

    static void writeSingleClaimsObject(JsonGenerator gen, String objectName, String... claims) throws IOException {
        gen.writeObjectFieldStart(objectName);
        for(String claim : claims)
            gen.writeObjectField(claim,null);
        gen.writeEndObject();
    }

    private void parseClaimsParameter(String claimsParameter) {
        try {
            cleanedJson = getCleanedJson(new ObjectMapper().readValue(claimsParameter, new TypeReference<>() {}));
        } catch (IOException e) {
        }
    }

    private Map<String,Object> getCleanedJson(Map<String,Object> json){
        cleanJsonObjectFromInvalidClaims(json, USERINFO);
        cleanJsonObjectFromInvalidClaims(json, ID_TOKEN);

        if(json.isEmpty())
            json = null;
        return json;
    }

    private void cleanJsonObjectFromInvalidClaims(Map<String, Object> json, String name){
        if(json.containsKey(name)) {
            cleanFromInvalidClaims((Map<String, Object>) json.get(name));
            if(((Map<String, Object>) json.get(name)).isEmpty())
                json.remove(name);
        }
    }

    private void cleanFromInvalidClaims(Map<String, Object> json) {
        ArrayList<String> toRemove = new ArrayList<>();
        for(String claim : json.keySet())
            if(!supportedClaims.contains(claim))
                toRemove.add(claim);
        for(String claim : toRemove)
            json.remove(claim);
    }

    public boolean hasClaims(){
        return cleanedJson != null;
    }

    public String toJson() throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(cleanedJson);
    }

    private Map<String,Object> getObject(String objectName){
        if(cleanedJson == null || cleanedJson.get(objectName) == null)
            return new HashMap<>();
        return (Map<String, Object>) cleanedJson.get(objectName);
    }

    private HashSet<String> getClaimsFromJsonObject(String objectName){
        return new HashSet<>(getObject(objectName).keySet());
    }

    public HashSet<String> getUserinfoClaims(){
        return getClaimsFromJsonObject(USERINFO);
    }

    public HashSet<String> getIdTokenClaims(){
        return getClaimsFromJsonObject(ID_TOKEN);
    }

}
