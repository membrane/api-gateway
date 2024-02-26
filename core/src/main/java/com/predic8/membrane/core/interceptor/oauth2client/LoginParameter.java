/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client;

import com.bornium.http.util.UriUtil;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@MCElement(name = "loginParameter")
public class LoginParameter {

    private String name;
    private String value;

    public LoginParameter() {}

    public LoginParameter(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public static String copyLoginParameters(Exchange exc, List<LoginParameter> loginParameters) throws Exception {
        StringBuilder sb = new StringBuilder();

        if (loginParameters.isEmpty())
            return sb.toString();

        Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
        loginParameters.forEach(lp -> {
            try {
                if (lp.getValue() != null) {
                    sb.append("&");
                    sb.append(lp.getName());
                    sb.append("=");
                    sb.append(UriUtil.encode(lp.getValue()));
                } else {
                    if (params.containsKey(lp.getName())) {
                        String encoded = UriUtil.encode(params.get(lp.getName()));
                        sb.append("&");
                        sb.append(lp.getName());
                        sb.append("=");
                        sb.append(encoded);
                    }
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        });

        return sb.toString();
    }

    public static List<LoginParameter> mergeParams(Map<String, String> params, List<LoginParameter> loginParameters) {
        var result = params.entrySet().stream()
                .filter(entry -> loginParameters.stream().anyMatch(lp -> entry.getKey().equals(lp.getName())))
                .map(entry -> new LoginParameter(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        loginParameters.stream()
                .filter(lp -> lp.getValue() != null)
                .forEach(lp -> {
                    var resultLp = result.stream().filter(rlp -> rlp.getName().equals(lp.getName())).findFirst();

                    if (resultLp.isPresent()) {
                        resultLp.get().setValue(lp.getValue());
                        return;
                    }

                    result.add(lp);
                });

        return result;
    }

    public String getName() {
        return name;
    }

    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    @MCAttribute
    public void setValue(String value) {
        this.value = value;
    }
}
