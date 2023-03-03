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

package com.predic8.membrane.core.util;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ClassFinder {

    private static final char PKG_SEPARATOR = '.';

    private static final char DIR_SEPARATOR = '/';

    private static final String CLASS_FILE_SUFFIX = ".class";

    public static List<Class<?>> find(ClassLoader loader, String scannedPackage) throws IOException, ClassNotFoundException {
        String scannedPath = scannedPackage.replace(PKG_SEPARATOR, DIR_SEPARATOR);
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
        Resource[] resources = resolver.getResources("classpath:" + scannedPath +"/*") ;
        List<Class<?>> classes = new ArrayList<Class<?>>();
        for (Resource resource: resources){
            String filename = resource.getFilename();
            Class<?> clazz = getClasses(resolver, scannedPackage, filename);
            if(clazz != null)
                classes.add(clazz);
        }
        return classes;
    }

    public static Class<?> getClasses(ResourcePatternResolver resolver, String scannedPackage, String s) throws ClassNotFoundException {
        if(s == null || s.isEmpty() || s.contains("$") || !s.contains(CLASS_FILE_SUFFIX))
            return null;
        s = scannedPackage + "." +  s.replaceFirst(Pattern.quote(CLASS_FILE_SUFFIX),"");
        return resolver.getClassLoader().loadClass(s);
    }
}
