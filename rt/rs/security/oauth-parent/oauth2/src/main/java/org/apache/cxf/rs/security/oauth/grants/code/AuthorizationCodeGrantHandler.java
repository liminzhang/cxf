/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.rs.security.oauth.grants.code;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.cxf.rs.security.oauth.common.Client;
import org.apache.cxf.rs.security.oauth.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth.provider.AccessTokenGrantHandler;
import org.apache.cxf.rs.security.oauth.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth.tokens.bearer.BearerAccessToken;
import org.apache.cxf.rs.security.oauth.utils.MD5SequenceGenerator;
import org.apache.cxf.rs.security.oauth.utils.OAuthUtils;



public class AuthorizationCodeGrantHandler implements AccessTokenGrantHandler {
    private static final String GRANT_TYPE = "grant_type";
    private static final String AUTHORIZATION_CODE_GRANT = "authorization_code";
    private static final String REDIRECT_URI = "redirect_uri";
    
    private static final long DEFAULT_TOKEN_LIFETIME = 3600L;
    
    private AuthorizationCodeDataProvider codeProvider;
    private long tokenLifetime = DEFAULT_TOKEN_LIFETIME;
    
    public List<String> getSupportedGrantTypes() {
        return Collections.singletonList(AUTHORIZATION_CODE_GRANT);
    }
    public ServerAccessToken createAccessToken(Client client, MultivaluedMap<String, String> params) 
        throws OAuthServiceException {
        
        ServerAuthorizationCodeGrant grant = codeProvider.removeCodeGrant(params.getFirst(GRANT_TYPE));
        if (grant == null) {
            return null;
        }
        if (OAuthUtils.isExpired(grant.getIssuedAt(), grant.getLifetime())) {
            throw new OAuthServiceException("invalid_grant");
        }
        
        String expectedRedirectUri = grant.getRedirectUri();
        if (expectedRedirectUri != null) {
            String providedRedirectUri = params.getFirst(REDIRECT_URI);
            if (providedRedirectUri == null || !providedRedirectUri.equals(expectedRedirectUri)) {
                throw new OAuthServiceException("invalid_request");
            }
        }
        BearerAccessToken token = new BearerAccessToken(client, 
                                                        generateTokenKey(),
                                                        tokenLifetime, 
                                                        System.currentTimeMillis() / 1000);
        token.setScopes(grant.getApprovedScopes());
        token.setSubject(grant.getSubject());
        return token;
    }
    public void setCodeProvider(AuthorizationCodeDataProvider codeProvider) {
        this.codeProvider = codeProvider;
    }
    
    protected String generateTokenKey() throws OAuthServiceException {
        try {
            byte[] bytes = UUID.randomUUID().toString().getBytes("UTF-8");
            return new MD5SequenceGenerator().generate(bytes);
        } catch (Exception ex) {
            throw new OAuthServiceException("server_error", ex);
        }
    }
    public void setTokenLifetime(long tokenLifetime) {
        this.tokenLifetime = tokenLifetime;
    }
    
}
