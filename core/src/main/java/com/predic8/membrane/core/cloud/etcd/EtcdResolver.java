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

package com.predic8.membrane.core.cloud.etcd;

import com.google.common.collect.Lists;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.resolver.Consumer;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.resolver.SchemaResolver;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;

@MCElement(name="etcdResolver")
public class EtcdResolver implements SchemaResolver{

    private String url;
    HashSet<Thread> etcdWatchThreads = new HashSet<Thread>();

    public String getUrl() {
        return url;
    }

    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public List<String> getSchemas() {
        return Lists.newArrayList("etcd");
    }

    @Override
    public InputStream resolve(String url) throws ResourceRetrievalException {
        String normalizedUrl = normalize(url);
        if(normalizedUrl.startsWith("etcd://")){
            // we keep the last slash
            normalizedUrl = normalizedUrl.substring(6);
        }
        int lastSlash = normalizedUrl.lastIndexOf("/");
        String baseKey = normalizedUrl.substring(0,lastSlash);
        String valueName = normalizedUrl.substring(lastSlash+1,normalizedUrl.length());
        EtcdResponse respGetValue = EtcdRequest.create(this.url,baseKey,"").getValue(valueName).sendRequest();
        if(!respGetValue.is2XX()) {
            throw new ResourceRetrievalException(url);
        }
        String value = respGetValue.getValue();
        return new ByteArrayInputStream(value.getBytes());
    }

    private String normalize(String url) {
        return url.replaceAll(Matcher.quoteReplacement("\\"),"/");
    }

    @Override
    public void observeChange(final String url, final Consumer<InputStream> consumer) throws ResourceRetrievalException {
        final Thread etcdWatcher = new Thread(new Runnable() {
            @Override
            public void run() {
                String normalizedUrl = normalize(url);
                if(normalizedUrl.startsWith("etcd://")){
                    // we keep the last slash
                    normalizedUrl = normalizedUrl.substring(6);
                }
                int lastSlash = normalizedUrl.lastIndexOf("/");
                String baseKey = normalizedUrl.substring(0,lastSlash);
                String valueName = normalizedUrl.substring(lastSlash+1,normalizedUrl.length());
                EtcdResponse respLongPollForChange = EtcdRequest.create(EtcdResolver.this.url,baseKey,"").getValue(valueName).longPoll().sendRequest();
                if(!respLongPollForChange.is2XX()){
                }
                try {
                    consumer.call(resolve(normalizedUrl));
                } catch (ResourceRetrievalException ignored) {
                }
                synchronized (etcdWatchThreads) {
                    etcdWatchThreads.remove(Thread.currentThread());
                }
            }

        });
        synchronized (etcdWatchThreads) {
            etcdWatchThreads.add(etcdWatcher);
        }
        etcdWatcher.start();
    }

    @Override
    public List<String> getChildren(String url) throws FileNotFoundException {
        return null;
    }

    @Override
    public long getTimestamp(String url) throws FileNotFoundException {
        return 0;
    }


}
