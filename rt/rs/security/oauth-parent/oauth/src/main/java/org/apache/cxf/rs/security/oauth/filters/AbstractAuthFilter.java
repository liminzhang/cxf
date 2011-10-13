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
package org.apache.cxf.rs.security.oauth.filters;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import net.oauth.OAuth;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.server.OAuthServlet;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.security.SimplePrincipal;
import org.apache.cxf.rs.security.oauth.data.AccessToken;
import org.apache.cxf.rs.security.oauth.data.Client;
import org.apache.cxf.rs.security.oauth.data.OAuthPermission;
import org.apache.cxf.rs.security.oauth.provider.OAuthDataProvider;
import org.apache.cxf.rs.security.oauth.utils.OAuthUtils;
import org.apache.cxf.security.SecurityContext;


public class AbstractAuthFilter {

    private static final Logger LOG = LogUtils.getL7dLogger(AbstractAuthFilter.class);
    private static final String[] REQUIRED_PARAMETERS = 
        new String[] {
            OAuth.OAUTH_CONSUMER_KEY,
            OAuth.OAUTH_TOKEN,
            OAuth.OAUTH_SIGNATURE_METHOD,
            OAuth.OAUTH_SIGNATURE,
            OAuth.OAUTH_TIMESTAMP,
            OAuth.OAUTH_NONCE
        };
    
    private OAuthDataProvider dataProvider;

    protected AbstractAuthFilter() {
        
    }
    
    public void setDataProvider(OAuthDataProvider provider) {
        dataProvider = provider;
    }
    
    public OAuthInfo handleOAuthRequest(HttpServletRequest req) throws
        Exception, OAuthProblemException {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "OAuth security filter for url: {0}", req.getRequestURL());
        }
        
        AccessToken accessToken = null;
        Client client = null;
        
        OAuthMessage oAuthMessage = OAuthServlet.getMessage(req, req.getRequestURL().toString());
        if (oAuthMessage.getParameter(OAuth.OAUTH_TOKEN) != null) {
            oAuthMessage.requireParameters(REQUIRED_PARAMETERS);

            accessToken = dataProvider.getAccessToken(oAuthMessage.getToken());

            //check if access token is not null
            if (accessToken == null) {
                throw new OAuthProblemException();
            }
            client = accessToken.getClient(); 
            
        } else {
            String consumerKey = oAuthMessage.getParameter(OAuth.OAUTH_CONSUMER_KEY);
            client = dataProvider.getClient(consumerKey);
            if (client == null) {
                throw new OAuthProblemException();
            }
        }

        OAuthUtils.validateMessage(oAuthMessage, client, accessToken);

        //check valid URI
        if (!checkRequestURI(req, getAllUris(client, accessToken))) {
            throw new OAuthProblemException();
        }
        
        List<OAuthPermission> permissions = dataProvider.getPermissionsInfo(
                getAllScopes(client, accessToken));
        for (OAuthPermission perm : permissions) {
            if (perm.getUri() != null 
                && !checkRequestURI(req, Collections.singletonList(perm.getUri()))) {
                throw new OAuthProblemException();
            }
            if (!perm.getHttpVerbs().isEmpty() 
                && !perm.getHttpVerbs().contains(req.getMethod())) {
                throw new OAuthProblemException();
            }
        }
        return new OAuthInfo(client, accessToken, permissions);
        
    }
    
    protected List<String> getAllScopes(Client client, AccessToken token) {
        List<String> scopes = new LinkedList<String>();
        if (token != null) {
            scopes.addAll(token.getScopes());
        }
        scopes.addAll(client.getScopes());
        return scopes;
    }
    
    protected List<String> getAllUris(Client client, AccessToken token) {
        List<String> uris = new LinkedList<String>();
        if (token != null) {
            uris.addAll(token.getUris());
        }
        uris.addAll(client.getUris());
        return uris;
    }

    protected boolean checkRequestURI(HttpServletRequest request, List<String> uris) {
        
        if (uris.isEmpty()) {
            return true;
        }
        String servletPath = request.getPathInfo();
        boolean foundValidScope = false;
        for (String uri : uris) {
            boolean wildcard = uri.endsWith("*");
            if (wildcard) {
                if (servletPath.startsWith(uri.substring(0, uri.length() - 1))) {
                    foundValidScope = true;
                    break;
                }
            } else {
                if (uri.equals(servletPath)) {
                    foundValidScope = true;
                    break;
                }
            }
        }
        return foundValidScope;
    }
    
    protected SecurityContext createSecurityContext(HttpServletRequest request, 
                                                    final OAuthInfo info) {
        request.setAttribute("oauth_authorities", info.getRoles());
        return new SecurityContext() {

            public Principal getUserPrincipal() {
                return new SimplePrincipal(info.getClient().getLoginName());
            }

            public boolean isUserInRole(String role) {
                List<String> roles = info.getRoles();
                for (String authority : roles) {
                    if (authority.equals(role)) {
                        return true;
                    }
                }
                return false;
            }
             
        };
    }
}
