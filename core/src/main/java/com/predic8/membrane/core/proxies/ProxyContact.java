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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.*;

/**
 * @description Person or team responsible for the API.
 */
@MCElement(name = "contact", component = false)
public class ProxyContact {

    private String name;
    private String email;
    private String url;
    private String organization;
    private String address;
    private String phone;
    private String twitter;
    private String github;
    private String photo;
    private String vCard;

    public String getName() {
        return name;
    }

    /**
     * @description Name of the person or team.
     * @example Platform Team
     */
    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    /**
     * @description Email address to contact.
     * @example platform@example.com
     */
    @MCAttribute
    public void setEmail(String email) {
        this.email = email;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description Web page with contact details.
     * @example https://example.com/platform
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public String getOrganization() {
        return organization;
    }

    /**
     * @description Name of the organization the person belongs to.
     * @example predic8 GmbH
     */
    @MCAttribute
    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getAddress() {
        return address;
    }

    /**
     * @description Postal address.
     * @example Koblenzer Str. 65, 53173 Bonn, Germany
     */
    @MCAttribute
    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    /**
     * @description Phone number including the country code.
     * @example +49 228 5552576-0
     */
    @MCAttribute
    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTwitter() {
        return twitter;
    }

    /**
     * @description X (Twitter) username, without the <code>@</code>.
     * @example membrane_api
     */
    @MCAttribute
    public void setTwitter(String twitter) {
        this.twitter = twitter;
    }

    public String getGithub() {
        return github;
    }

    /**
     * @description GitHub username or organization.
     * @example membrane
     */
    @MCAttribute
    public void setGithub(String github) {
        this.github = github;
    }

    public String getPhoto() {
        return photo;
    }

    /**
     * @description URL of an image representing the person or team.
     * @example https://example.com/team.png
     */
    @MCAttribute
    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getVCard() {
        return vCard;
    }

    /**
     * @description URL of a vCard (RFC 6350) with the full contact details.
     * @example https://example.com/team.vcf
     */
    @MCAttribute(attributeName = "vCard")
    public void setVCard(String vCard) {
        this.vCard = vCard;
    }
}
