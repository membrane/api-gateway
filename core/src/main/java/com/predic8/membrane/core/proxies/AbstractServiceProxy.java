/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;

import java.util.List;

import static com.predic8.membrane.core.util.NetworkUtil.addBracketsIfIPv6;
import static java.util.stream.Collectors.joining;

public abstract class AbstractServiceProxy extends SSLableProxy {

    private ProxyInfo info;
    private List<String> tags = List.of();

    @Override
    public void init() {
        super.init();
        if (target.getSslParser() != null)
            setSslOutboundContext(new StaticSSLContext(target.getSslParser(), router.getResolverMap(), getBeanBaseLocation()));
        target.init(router);
    }

    public String getHost() {
        return key.getHost();
    }

    /**
     * @description Restricts to requests whose <code>Host</code> header matches one of the
     *              given hostnames. Separate multiple hostnames with spaces. The asterisk
     *              <code>*</code> matches any number of characters, including zero, for basic globbing.
     * @default <i>not set</i>
     * @example predic8.de *.predic8.de
     */
    @MCAttribute
    public void setHost(String host) {
        if (!(key instanceof ServiceProxyKey sp))
            return;
        sp.setHost(host);
    }

    public Path getPath() {
        if (!(key instanceof AbstractRuleKey ark))
            return null;

        if (!ark.isUsePathPattern())
            return null;
        return new Path(ark.isPathRegExp(), ark.getPath());
    }

    public ProxyInfo getInfo() {
        return info;
    }

    /**
     * @description Metadata about the API that API catalogs publish, such as whom to contact. It
     * takes precedence over the same metadata in an OpenAPI document of the API.
     */
    @MCChildElement(order = 20)
    public void setInfo(ProxyInfo info) {
        this.info = info;
    }

    public List<String> getTags() {
        return tags;
    }

    /**
     * @description Labels for the API. API catalogs list them, and the discovery plugins can select
     * the APIs they publish by them.
     * @example [external, partner]
     */
    @MCChildElement(allowForeign = true, order = 21)
    public void setTags(List<String> tags) {
        this.tags = List.copyOf(tags);
    }

    /**
     * @description Restricts to requests whose path matches, either by prefix (starts-with)
     *              or, when enabled, by regular expression. In a <code>soapProxy</code>, setting a path
     *              also rewrites the paths of SOAP requests and the service addresses in the WSDL
     *              accordingly.
     */
    @MCChildElement(order = 50)
    public void setPath(Path path) {
        AbstractRuleKey k = (AbstractRuleKey) key;
        k.setUsePathPattern(path != null);
        if (path != null) {
            k.setPathRegExp(path.isRegExp());
            k.setPath(path.getUri());
        }
    }

    protected Target target = new Target();

    public Target getTarget() {
        return target;
    }

    /**
     * @description The backend server that requests are forwarded to after passing through the flow.
     */
    @MCChildElement(order = 150)
    public void setTarget(Target target) {
        this.target = target;
    }

    public String getTargetScheme() {
        return getSslOutboundContext() != null ? "https" : "http";
    }

    public String getTargetHost() {
        return target.getHost();
    }

    public int getTargetPort() {
        return target.getPort();
    }

    public String getTargetURL() {
        return target.getUrl();
    }

    public SSLParser getTargetSSL() {
        return target.getSslParser();
    }

    @Override
    public boolean isTargetAdjustHostHeader() {
        return target.isAdjustHostHeader();
    }

    /**
     * @return The name, then the URL the API listens on
     */
    @Override
    public String getStartupLabel() {
        return getName() + "  " + buildUrl();
    }

    /**
     * @return URLs the API listens on, comma-separated
     */
    String buildUrl() {
        final var origin = "%s://%s:%d".formatted(getProtocol(), displayHost(key), key.getPort());
        return startupPaths().stream()
                .map(path -> origin + path)
                .collect(joining(", "));
    }

    /**
     * @return Paths of the URLs shown in the startup log
     */
    protected List<String> startupPaths() {
        final var path = key.getPath();
        return List.of(path != null ? path : "");
    }

    private static String displayHost(RuleKey key) {
        if (key.getIp() != null) {
            return addBracketsIfIPv6(key.getIp());
        }
        final var host = key.getHost();
        return addBracketsIfIPv6((host == null || host.equals("*")) ? "127.0.0.1" : host);
    }

}
