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

import java.util.concurrent.ConcurrentHashMap;

public class ClaimRenamer {
    static ConcurrentHashMap<String,String> oldNameToNewName = new ConcurrentHashMap<String, String>();

    static{
        addConversion("sub","username");
    }

    public static void addConversion(String oldName, String newName){
        oldNameToNewName.put(oldName, newName);
    }

    public static String convert(String oldName){
        if(!oldNameToNewName.containsKey(oldName))
            return oldName;
        return oldNameToNewName.get(oldName);
    }
}
