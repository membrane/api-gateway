/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.acl;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import static com.predic8.membrane.core.interceptor.acl.IpAddress.ipVersion.IPV4;
import static com.predic8.membrane.core.interceptor.acl.IpAddress.ipVersion.IPV6;
import static com.predic8.membrane.core.util.NetworkUtil.removeBracketsIfPresent;
import static java.net.InetAddress.getByName;

/**
 * IP address of the request to test against ACLs.
 */
public final class IpAddress {

    private final InetAddress address;
    private final ipVersion version;

    private String hostname = "";

    private IpAddress(InetAddress address) {
        this.address = Objects.requireNonNull(address, "address");
        if (address instanceof Inet4Address) {
            this.version = IPV4;
        } else if (address instanceof Inet6Address) {
            this.version = IPV6;
        } else {
            // Should never happen
            throw new IllegalArgumentException("Unsupported InetAddress type: " + address.getClass().getName());
        }
    }

    public static IpAddress of(InetAddress address) {
        return new IpAddress(address);
    }

    public static IpAddress parse(String raw) {
        Objects.requireNonNull(raw, "raw");

        try {
            return new IpAddress(getByName(removeBracketsIfPresent(raw.trim())));
        } catch (UnknownHostException e) {
            // Incoming values should be valid
            throw new IllegalArgumentException("Invalid IP address: " + raw, e);
        }
    }

    public ipVersion version() {
        return version;
    }

    public InetAddress getInetAddress() {
        return address;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = (hostname == null) ? "" : hostname;
    }

    public enum ipVersion {
        IPV4, IPV6
    }

    @Override
    public String toString() {
        var s = "ip: " + address;
        if (hostname != null && !hostname.isEmpty())
            return  " hostname: " + hostname;
        return s;
    }
}
