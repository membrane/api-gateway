/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.apimanagement.rateLimiter;

public class LimitReachedAnswer {
    PolicyRateLimit prl = null;
    
    private LimitReachedAnswer(){
        
    }
    
    public static LimitReachedAnswer createLimitNotReached(){
        return new LimitReachedAnswer();
    }
    
    public static LimitReachedAnswer createLimitReached(PolicyRateLimit prl){
        LimitReachedAnswer result = new LimitReachedAnswer();
        result.prl = prl;
        return result;
    }
    
    public boolean isLimitReached(){
        return prl != null;
    }

    public PolicyRateLimit getPrl() {
        return prl;
    }
}
