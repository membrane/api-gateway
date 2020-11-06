/*
 * Copyright 2019 predic8 GmbH, www.predic8.com
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
package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.http.Exchange;
import com.bornium.security.oauth2openid.Constants;
import com.bornium.security.oauth2openid.providers.Session;
import com.bornium.security.oauth2openid.server.ServerServices;
import com.bornium.security.oauth2openid.server.endpoints.Endpoint;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;
import com.predic8.membrane.core.interceptor.oauth2.ClaimRenamer;
import com.predic8.membrane.core.interceptor.oauth2.ConsentPageFile;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.functionalInterfaces.Function;

import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Map;

public class LoginEndpoint extends Endpoint {

    LoginDialog2 loginDialog;
    URIFactory uriFactory;
    private String loginPath;
    private final ConsentPageFile csf;

    public LoginEndpoint(Router router, ServerServices serverServices, UserDataProvider userDataProvider, com.predic8.membrane.core.interceptor.session.SessionManager sessionManager, String loginDialogLocation, String loginPath, ConsentPageFile csf, String... paths) throws Exception {
        super(serverServices, paths);
        this.loginPath = loginPath;
        this.csf = csf;
        loginDialog = new LoginDialog2(userDataProvider,null,sessionManager,null,loginDialogLocation,loginPath,true,null);
        loginDialog.init(router);
        uriFactory = router.getUriFactory();
    }

    @Override
    public void invokeOn(Exchange exchange) throws Exception {
        com.predic8.membrane.core.exchange.Exchange exc = Convert.convertToMembraneExchange(exchange);

        fixTarget(exc);
        initConsentWhenNeeded(exchange, exc);

        loginDialog.handleLoginRequest(exc);

        postProcessLoginDialogResult(exchange);

        convertAndReplaceFromMembraneExchange(exchange, exc);
    }

    private void convertAndReplaceFromMembraneExchange(Exchange exchange, com.predic8.membrane.core.exchange.Exchange exc) {
        Exchange conv = Convert.convertFromMembraneExchange(exc);
        exchange.setRequest(conv.getRequest());
        exchange.setResponse(conv.getResponse());
        exchange.setProperties(conv.getProperties());
    }

    private void fixTarget(com.predic8.membrane.core.exchange.Exchange exc) throws Exception {
        exc.getRequest().setUri(exc.getRequest().getUri() + "?" + URLParamUtil.encode(setTargetInParams(exc)));
    }

    private void initConsentWhenNeeded(Exchange exchange, com.predic8.membrane.core.exchange.Exchange exc) throws Exception {
        if(exc.getRequest().getUri().contains("consent")){
            Session s = this.serverServices.getProvidedServices().getSessionProvider().getSession(exchange);
            s.putValue(ConsentPageFile.PRODUCT_NAME, csf.getProductName());
            s.putValue(ConsentPageFile.LOGO_URL, csf.getLogoUrl());
            s.putValue(ConsentPageFile.SCOPE_DESCRIPTIONS, getScopeDescriptions(s.getValue(ParamNames.SCOPE).split(" ")));
            s.putValue(ConsentPageFile.CLAIM_DESCRIPTIONS, getClaimDescriptions(processClaimsParameterToClaimsString(s.getValue(ParamNames.CLAIMS))));
        }
    }

    private void postProcessLoginDialogResult(Exchange exchange) throws Exception {
        Session s = this.serverServices.getProvidedServices().getSessionProvider().getSession(exchange);
        String authLevel = s.getValue("_internal_auth_level");
        String consent = s.getValue("consent");
        if("VERIFIED".equals(authLevel)) {
            s.putValue(Constants.SESSION_LOGGED_IN, "yes");
            Map<String, String> params = URLParamUtil.parseQueryString(exchange.getRequest().getBody());
            this.serverServices.getProvidedServices().getUserDataProvider().verifyUser(params.get("username"),params.get("password"));
        }
        if(consent != null && consent.equals("true"))
            s.putValue(Constants.SESSION_CONSENT_GIVEN,"yes");
    }

    private Map<String, String> setTargetInParams(com.predic8.membrane.core.exchange.Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);
        String target = params.get("target");
        if(target == null || target.isEmpty()){
//            params.put("target",loginPath + "login");
            params.put("target",exc.getRequest().getUri());
        }else{
            if(target.equals("/login/login"))
                params.put("target",loginPath + "consent");
            else if(target.equals("/login/consent")){
                params.put("target","/auth2");
            }
        }
        return params;
    }

    private String[] processClaimsParameterToClaimsString(String claimsParam) {
        ClaimsParameter cp = new ClaimsParameter(this.serverServices.getSupportedClaims().getClaims(),claimsParam);
        StringBuilder builder = new StringBuilder();

        HashSet<String> userinfo = cp.getUserinfoClaims();
        for(String claim : userinfo)
            builder.append(" ").append(claim);

        HashSet<String> idToken = cp.getIdTokenClaims();
        for(String claim : idToken)
            builder.append(" ").append(claim);

        return builder.toString().trim().split(" ");
    }

    private String getClaimDescriptions(String[] claims) throws UnsupportedEncodingException {
        return createDescription(claims, new Function<String, String>() {
            @Override
            public String call(String param) {
                return ClaimRenamer.convert(param);
            }
        },new Function<String, String>() {
            @Override
            public String call(String claimParam) {
                return csf.convertClaim(ClaimRenamer.convert(claimParam));
            }
        });
    }

    private String getScopeDescriptions(String[] scopes) throws UnsupportedEncodingException {
        return createDescription(scopes, new Function<String, String>() {
            @Override
            public String call(String param) {
                if(param.equals("openid"))
                    return "";
                return param;
            }
        },new Function<String, String>() {
            @Override
            public String call(String scopeParam) {
                return csf.convertScope(scopeParam);
            }
        });
    }

    private String createDescription(String[] params, Function<String,String> paramNameConverter, Function<String,String> paramValueConverter) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        HashSet<String> alreadyAddedParams = new HashSet<String>();
        for(String param : params) {
            String correctedParamName = paramNameConverter.call(param);
            if(!correctedParamName.isEmpty() && !alreadyAddedParams.contains(correctedParamName)){
                alreadyAddedParams.add(correctedParamName);
                builder.append(" ").append(correctedParamName).append(" ").append(OAuth2Util.urlencode(paramValueConverter.call(param)));
            }
        }
        return builder.toString().trim();
    }

    @Override
    public String getScope(Exchange exchange) throws Exception {
        return null;
    }
}
