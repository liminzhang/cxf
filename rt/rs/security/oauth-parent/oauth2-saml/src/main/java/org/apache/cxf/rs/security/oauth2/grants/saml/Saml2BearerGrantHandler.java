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
package org.apache.cxf.rs.security.oauth2.grants.saml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MultivaluedMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.cxf.common.util.Base64Exception;
import org.apache.cxf.jaxrs.utils.HttpUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.rs.security.common.CryptoLoader;
import org.apache.cxf.rs.security.common.SecurityUtils;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.grants.AbstractGrantHandler;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.saml.Constants;
import org.apache.cxf.rs.security.oauth2.saml.SamlOAuthValidator;
import org.apache.cxf.rs.security.oauth2.utils.Base64UrlUtility;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;
import org.apache.cxf.rs.security.oauth2.utils.OAuthUtils;
import org.apache.cxf.rs.security.saml.authorization.JAXRSSAMLSecurityContext;
import org.apache.cxf.rs.security.saml.authorization.SecurityContextProvider;
import org.apache.cxf.rs.security.saml.authorization.SecurityContextProviderImpl;
import org.apache.cxf.security.SecurityContext;
import org.apache.cxf.security.transport.TLSSessionInfo;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.handler.RequestData;
import org.apache.ws.security.handler.WSHandlerConstants;
import org.apache.ws.security.saml.ext.AssertionWrapper;
import org.apache.ws.security.validate.Credential;
import org.apache.ws.security.validate.SamlAssertionValidator;
import org.apache.ws.security.validate.Validator;
/**
 * The "SAML2 Bearer" grant handler
 */
public class Saml2BearerGrantHandler extends AbstractGrantHandler {
    private static final String ENCODED_SAML2_BEARER_GRANT;
    static {
        WSSConfig.init();
        //  AccessTokenService may be configured with the form provider
        // which will not decode by default - so listing both the actual 
        // and encoded grant type value will help
        ENCODED_SAML2_BEARER_GRANT = HttpUtils.urlEncode(Constants.SAML2_BEARER_GRANT, "UTF-8");
    }
    private Validator samlValidator = new SamlAssertionValidator();
    private SamlOAuthValidator samlOAuthValidator = new SamlOAuthValidator(); 
    private SecurityContextProvider scProvider = new SecurityContextProviderImpl(); 
    
    public Saml2BearerGrantHandler() {
        super(Arrays.asList(Constants.SAML2_BEARER_GRANT, ENCODED_SAML2_BEARER_GRANT));
    }
    
    public void setSamlValidator(Validator validator) {
        samlValidator = validator;
    }
    
    public void setSamlOAuthValidator(SamlOAuthValidator validator) {
        samlOAuthValidator = validator;
    }
    
    public void setSecurityContextProvider(SecurityContextProvider p) {
        scProvider = p;
    }
    
    public ServerAccessToken createAccessToken(Client client, MultivaluedMap<String, String> params)
        throws OAuthServiceException {
        
        String assertion = params.getFirst(Constants.CLIENT_GRANT_ASSERTION_PARAM);
        if (assertion == null) {
            throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
        }
        try {   
            InputStream tokenStream = decodeAssertion(assertion);
            Element token = readToken(tokenStream);
            AssertionWrapper assertionWrapper = new AssertionWrapper(token);
            
            Message message = PhaseInterceptorChain.getCurrentMessage();
    
            validateToken(message, assertionWrapper);
            UserSubject grantSubject = getGrantSubject(message, assertionWrapper);
            
            return doCreateAccessToken(client, 
                                       grantSubject,
                                       Constants.SAML2_BEARER_GRANT,
                                       OAuthUtils.parseScope(params.getFirst(OAuthConstants.SCOPE)));
        } catch (OAuthServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OAuthServiceException(OAuthConstants.INVALID_GRANT, ex);
        }
    }

    protected UserSubject getGrantSubject(Message message, AssertionWrapper wrapper) {
        SecurityContext sc = scProvider.getSecurityContext(message, wrapper);
        if (sc instanceof JAXRSSAMLSecurityContext) {
            JAXRSSAMLSecurityContext jaxrsSc = (JAXRSSAMLSecurityContext)sc;
            Set<Principal> rolesP = jaxrsSc.getUserRoles();
            List<String> roles = new ArrayList<String>();
            if (roles != null) {
                for (Principal p : rolesP) {
                    roles.add(p.getName());
                }
            }
            return new SamlUserSubject(jaxrsSc.getUserPrincipal().getName(), 
                                       roles,
                                       jaxrsSc.getClaims());
        } else {
            return new UserSubject(sc.getUserPrincipal().getName());
        }
        
    }
    
    private InputStream decodeAssertion(String assertion) {
        try {
            byte[] deflatedToken = Base64UrlUtility.decode(assertion);
            return new ByteArrayInputStream(deflatedToken); 
        } catch (Base64Exception ex) {
            throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
        }   
    }
    

    protected Element readToken(InputStream tokenStream) {
        
        try {
            Document doc = StaxUtils.read(new InputStreamReader(tokenStream, "UTF-8"));
            return doc.getDocumentElement();
        } catch (Exception ex) {
            throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
        }
    }

    protected void validateToken(Message message, AssertionWrapper assertion) {
        try {
            RequestData data = new RequestData();
            if (assertion.isSigned()) {
                WSSConfig cfg = WSSConfig.getNewInstance(); 
                data.setWssConfig(cfg);
                data.setCallbackHandler(SecurityUtils.getCallbackHandler(message, this.getClass()));
                try {
                    data.setSigCrypto(new CryptoLoader().getCrypto(message,
                                                SecurityConstants.SIGNATURE_CRYPTO,
                                                SecurityConstants.SIGNATURE_PROPERTIES));
                } catch (IOException ex) {
                    throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
                }
                data.setEnableRevocation(MessageUtils.isTrue(
                    message.getContextualProperty(WSHandlerConstants.ENABLE_REVOCATION)));
                assertion.verifySignature(data, null);
            } else if (getTLSCertificates(message) == null) {
                throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
            }
            
            if (samlValidator != null) {
                Credential credential = new Credential();
                credential.setAssertion(assertion);
                samlValidator.validate(credential, data);
            }
            samlOAuthValidator.validate(message, assertion);
        } catch (Exception ex) {
            throw new OAuthServiceException(OAuthConstants.INVALID_GRANT, ex);
        }
    }
    
    private Certificate[] getTLSCertificates(Message message) {
        TLSSessionInfo tlsInfo = message.get(TLSSessionInfo.class);
        return tlsInfo != null ? tlsInfo.getPeerCertificates() : null;
    }
    
    protected void setSecurityContext(Message message, AssertionWrapper wrapper) {
        if (scProvider != null) {
            SecurityContext sc = scProvider.getSecurityContext(message, wrapper);
            message.put(SecurityContext.class, sc);
        }
    }
}
