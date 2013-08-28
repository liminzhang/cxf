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

package org.apache.cxf.rs.security.oauth2.grants.code;

import java.io.StringWriter;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.cxf.common.util.Base64Exception;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.grants.AbstractGrantHandler;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.utils.Base64UrlUtility;
import org.apache.cxf.rs.security.oauth2.utils.MessageDigestGenerator;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;
import org.apache.cxf.rs.security.oauth2.utils.OAuthUtils;


/**
 * Authorization Code Grant Handler
 */
public class AuthorizationCodeGrantHandler extends AbstractGrantHandler {
    
    public AuthorizationCodeGrantHandler() {
        super(OAuthConstants.AUTHORIZATION_CODE_GRANT);
    }
    
    public ServerAccessToken createAccessToken(Client client, MultivaluedMap<String, String> params) 
        throws OAuthServiceException {
                
        // Get the grant representation from the provider 
        String codeValue = params.getFirst(OAuthConstants.AUTHORIZATION_CODE_VALUE);
        ServerAuthorizationCodeGrant grant = 
            ((AuthorizationCodeDataProvider)getDataProvider()).removeCodeGrant(codeValue);
        if (grant == null) {
            return null;
        }
        // check it has not expired, the client ids are the same
        if (OAuthUtils.isExpired(grant.getIssuedAt(), grant.getLifetime())) {
            throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
        }
        if (!grant.getClient().getClientId().equals(client.getClientId())) {
            throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
        }
        // redirect URIs must match too
        String expectedRedirectUri = grant.getRedirectUri();
        String providedRedirectUri = params.getFirst(OAuthConstants.REDIRECT_URI);
        if (providedRedirectUri != null) {
            if (expectedRedirectUri == null || !providedRedirectUri.equals(expectedRedirectUri)) {
                throw new OAuthServiceException(OAuthConstants.INVALID_REQUEST);
            }
        } else if (expectedRedirectUri == null && !isCanSupportPublicClients()
            || expectedRedirectUri != null 
                && (client.getRedirectUris().size() != 1 
                || !client.getRedirectUris().contains(expectedRedirectUri))) {
            throw new OAuthServiceException(OAuthConstants.INVALID_REQUEST);
        }
        
        String tempClientSecretHash = grant.getTempClientSecretHash();
        if (tempClientSecretHash != null) {
            String tempClientSecret = params.getFirst(OAuthConstants.TEMP_CLIENT_SECRET);
            if (!compareTcshWithTch(tempClientSecretHash, tempClientSecret)) {
                throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
            }
        }
        
        return doCreateAccessToken(client, 
                                   grant.getSubject(), 
                                   grant.getApprovedScopes(),
                                   grant.getAudience());
    }
    
    private boolean compareTcshWithTch(String tempClientSecretHash, String tempClientSecret) {
        if (tempClientSecret == null) {
            return false;
        }
        MessageDigestGenerator mdg = new MessageDigestGenerator();
        byte[] digest = mdg.createDigest(tempClientSecret, "SHA-256");
        int length = digest.length > 128 / 8 ? 128 / 8 : digest.length;
        
        StringWriter stringWriter = new StringWriter();
        try {
            Base64UrlUtility.encode(digest, 0, length, stringWriter);
        } catch (Base64Exception e) {
            throw new OAuthServiceException("server_error", e);
        }
        String expectedHash = stringWriter.toString();
        return tempClientSecretHash.equals(expectedHash);
        
    }
}
