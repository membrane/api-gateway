/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.authentication.session;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.predic8.membrane.annot.Required;
import java.util.*;
import java.util.concurrent.TimeUnit;



/**
 * @description Caching User Data provider caches previous successful logins in order to make authentication faster
 */
@MCElement(name="cachingUserDataProvider", topLevel=false)
public class CachingUserDataProvider implements UserDataProvider {

    private static final Logger log = LoggerFactory.getLogger(CachingUserDataProvider.class.getName());
    private UserDataProvider userDataProvider;
    private Cache<ImmutableMap<String, String>, Boolean> cache;
    private int expireTime;
    private int maxSize;

    @Override
    public Map<String, String> verify(Map<String, String> postData) {
        ImmutableMap<String, String> temp = ImmutableMap.copyOf(postData);
        Boolean isCached = cache.getIfPresent(temp);
        if(isCached != null && isCached){
            log.info(String.format("User %s verified through cache", postData.getOrDefault("username", "default")));
            return postData;
        }
        else{
            try {
                ImmutableMap<String, String> res = ImmutableMap.copyOf(userDataProvider.verify(postData));
                cache.put(temp, true);
                log.info(String.format("User %s verified through data provider", postData.getOrDefault("username", "default")));
                return res;
            } catch (NoSuchElementException e) {
            }

        throw new NoSuchElementException();
        }
    }

    public UserDataProvider getUserDataProvider() {
        return userDataProvider;
    }

    /**
     * @description The <i>user data provider</i> verifying a combination of a username with a password.
     */
    @Required
    @MCChildElement(order = 10)
    public void setUserDataProvider(UserDataProvider userDataProvider) {
        this.userDataProvider = userDataProvider;
    }



    @Override
    public void init(Router router) {
        userDataProvider.init(router);
        cache = CacheBuilder.newBuilder()
                .maximumSize(this.getMaxSize())
                .expireAfterWrite(this.getExpiryTime(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * @description Expire time for cache in milliseconds
     * @example 600000
     */
    @Required
    @MCAttribute(attributeName = "expiry")
    public void setExpiryTime(String expiry) {
        this.expireTime = Integer.parseInt(expiry);
    }

    public int getExpiryTime() {
        return this.expireTime;
    }

    /**
     * @description Max cache size
     * @example 10000
     */
    @Required
    @MCAttribute(attributeName = "maxSize")
    public void setMaxSize(String maxSize) {
        this.maxSize = Integer.parseInt(maxSize);
    }

    public int getMaxSize() {
        return this.maxSize;
    }

}
