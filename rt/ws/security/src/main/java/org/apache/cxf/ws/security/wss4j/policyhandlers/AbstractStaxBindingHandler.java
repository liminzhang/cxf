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

package org.apache.cxf.ws.security.wss4j.policyhandlers;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.callback.CallbackHandler;
import javax.xml.namespace.QName;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.i18n.Message;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.cxf.ws.policy.PolicyException;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.neethi.Assertion;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.policy.SP11Constants;
import org.apache.wss4j.policy.SP12Constants;
import org.apache.wss4j.policy.SPConstants;
import org.apache.wss4j.policy.SPConstants.IncludeTokenType;
import org.apache.wss4j.policy.model.AbstractBinding;
import org.apache.wss4j.policy.model.AbstractToken;
import org.apache.wss4j.policy.model.AbstractTokenWrapper;
import org.apache.wss4j.policy.model.AlgorithmSuite.AlgorithmSuiteType;
import org.apache.wss4j.policy.model.KeyValueToken;
import org.apache.wss4j.policy.model.Layout;
import org.apache.wss4j.policy.model.Layout.LayoutType;
import org.apache.wss4j.policy.model.SamlToken;
import org.apache.wss4j.policy.model.SamlToken.SamlTokenType;
import org.apache.wss4j.policy.model.SupportingTokens;
import org.apache.wss4j.policy.model.UsernameToken;
import org.apache.wss4j.policy.model.UsernameToken.PasswordType;
import org.apache.wss4j.policy.model.Wss10;
import org.apache.wss4j.policy.model.Wss11;
import org.apache.wss4j.policy.model.X509Token;
import org.apache.wss4j.policy.model.X509Token.TokenType;
import org.apache.wss4j.stax.ext.WSSConstants;
import org.apache.xml.security.stax.ext.SecurePart;
import org.apache.xml.security.stax.ext.SecurePart.Modifier;

/**
 * 
 */
public abstract class AbstractStaxBindingHandler {
    private static final Logger LOG = LogUtils.getL7dLogger(AbstractStaxBindingHandler.class);
    protected boolean timestampAdded;
    protected Set<SecurePart> encryptedTokensList = new HashSet<SecurePart>();
    private final Map<String, Object> properties;
    private final SoapMessage message;
    
    public AbstractStaxBindingHandler(Map<String, Object> properties, SoapMessage msg) {
        this.properties = properties;
        this.message = msg;
    }

    protected SecurePart addUsernameToken(UsernameToken usernameToken) {
        IncludeTokenType includeToken = usernameToken.getIncludeTokenType();
        if (!isTokenRequired(includeToken)) {
            return null;
        }

        Map<String, Object> config = getProperties();
        
        // Action
        if (config.containsKey(ConfigurationConstants.ACTION)) {
            String action = (String)config.get(ConfigurationConstants.ACTION);
            config.put(ConfigurationConstants.ACTION, 
                       action + " " + ConfigurationConstants.USERNAME_TOKEN);
        } else {
            config.put(ConfigurationConstants.ACTION, 
                       ConfigurationConstants.USERNAME_TOKEN);
        }

        // Password Type
        PasswordType passwordType = usernameToken.getPasswordType();
        if (passwordType == PasswordType.HashPassword) {
            config.put(ConfigurationConstants.PASSWORD_TYPE, WSConstants.PW_DIGEST);
        } else if (passwordType == PasswordType.NoPassword) {
            config.put(ConfigurationConstants.PASSWORD_TYPE, WSConstants.PW_NONE);
        } else {
            config.put(ConfigurationConstants.PASSWORD_TYPE, WSConstants.PW_TEXT);
        }

        // Nonce + Created
        if (usernameToken.isNonce()) {
            config.put(ConfigurationConstants.ADD_USERNAMETOKEN_NONCE, "true");
        }
        if (usernameToken.isCreated()) {
            config.put(ConfigurationConstants.ADD_USERNAMETOKEN_CREATED, "true");
        }
        
        return new SecurePart(WSSConstants.TAG_wsse_UsernameToken, Modifier.Element);
    }
    
    protected SecurePart addSamlToken(
        SamlToken token, 
        boolean signed,
        boolean endorsing
    ) throws WSSecurityException {
        IncludeTokenType includeToken = token.getIncludeTokenType();
        if (!isTokenRequired(includeToken)) {
            return null;
        }
        
        Map<String, Object> config = getProperties();
        
        //
        // Get the SAML CallbackHandler
        //
        Object o = message.getContextualProperty(SecurityConstants.SAML_CALLBACK_HANDLER);
    
        CallbackHandler handler = null;
        if (o instanceof CallbackHandler) {
            handler = (CallbackHandler)o;
        } else if (o instanceof String) {
            try {
                handler = (CallbackHandler)ClassLoaderUtils
                    .loadClass((String)o, this.getClass()).newInstance();
            } catch (Exception e) {
                handler = null;
            }
        }
        if (handler == null) {
            policyNotAsserted(token, "No SAML CallbackHandler available");
            return null;
        }
        config.put(ConfigurationConstants.SAML_CALLBACK_REF, handler);
        
        // Action
        String samlAction = ConfigurationConstants.SAML_TOKEN_UNSIGNED;
        if (signed || endorsing) {
            samlAction = ConfigurationConstants.SAML_TOKEN_SIGNED;
        }
        
        if (config.containsKey(ConfigurationConstants.ACTION)) {
            String action = (String)config.get(ConfigurationConstants.ACTION);
            config.put(ConfigurationConstants.ACTION, action + " " + samlAction);
        } else {
            config.put(ConfigurationConstants.ACTION, samlAction);
        }
        
        QName qname = WSSConstants.TAG_saml2_Assertion;
        SamlTokenType tokenType = token.getSamlTokenType();
        if (tokenType == SamlTokenType.WssSamlV11Token10 || tokenType == SamlTokenType.WssSamlV11Token11) {
            qname = WSSConstants.TAG_saml_Assertion;
        }
        
        return new SecurePart(qname, Modifier.Element);
    }
    
    protected void policyNotAsserted(Assertion assertion, String reason) {
        if (assertion == null) {
            return;
        }
        LOG.log(Level.FINE, "Not asserting " + assertion.getName() + ": " + reason);
        AssertionInfoMap aim = message.get(AssertionInfoMap.class);
        Collection<AssertionInfo> ais = aim.get(assertion.getName());
        if (ais != null) {
            for (AssertionInfo ai : ais) {
                if (ai.getAssertion() == assertion) {
                    ai.setNotAsserted(reason);
                }
            }
        }
        if (!assertion.isOptional()) {
            throw new PolicyException(new Message(reason, LOG));
        }
    }
    
    protected void configureTimestamp(AssertionInfoMap aim) {
        Map<String, Object> config = getProperties();
        
        AbstractBinding binding = getBinding(aim);
        if (binding != null && binding.isIncludeTimestamp()) {
            // Action
            if (config.containsKey(ConfigurationConstants.ACTION)) {
                String action = (String)config.get(ConfigurationConstants.ACTION);
                config.put(ConfigurationConstants.ACTION, 
                           action + " " + ConfigurationConstants.TIMESTAMP);
            } else {
                config.put(ConfigurationConstants.ACTION, 
                           ConfigurationConstants.TIMESTAMP);
            }
            
            timestampAdded = true;
        }
    }
    
    protected void configureLayout(AssertionInfoMap aim) {
        Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, SPConstants.LAYOUT);
        for (AssertionInfo ai : ais) {
            Layout layout = (Layout)ai.getAssertion();
            ai.setAsserted(true);
            if (layout.getLayoutType() == LayoutType.LaxTsLast) {
                // TODO re-order action list
            } else if (layout.getLayoutType() == LayoutType.LaxTsFirst) {
                // TODO re-order action list
            }
        }
    }

    protected AbstractBinding getBinding(AssertionInfoMap aim) {
        Collection<AssertionInfo> ais = 
            getAllAssertionsByLocalname(aim, SPConstants.TRANSPORT_BINDING);
        if (ais != null && ais.size() > 0) {
            return (AbstractBinding)ais.iterator().next().getAssertion();
        }
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.SYMMETRIC_BINDING);
        if (ais != null && ais.size() > 0) {
            return (AbstractBinding)ais.iterator().next().getAssertion();
        }
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.ASYMMETRIC_BINDING);
        if (ais != null && ais.size() > 0) {
            return (AbstractBinding)ais.iterator().next().getAssertion();
        }
        
        return null;
    }
    
    protected boolean isRequestor() {
        return MessageUtils.isRequestor(message);
    }
    
    protected boolean isTokenRequired(IncludeTokenType includeToken) {
        if (includeToken == IncludeTokenType.INCLUDE_TOKEN_NEVER) {
            return false;
        } else if (includeToken == IncludeTokenType.INCLUDE_TOKEN_ALWAYS) {
            return true;
        } else {
            boolean initiator = MessageUtils.isRequestor(message);
            if (initiator && (includeToken == IncludeTokenType.INCLUDE_TOKEN_ALWAYS_TO_RECIPIENT
                || includeToken == IncludeTokenType.INCLUDE_TOKEN_ONCE)) {
                return true;
            } else if (!initiator && includeToken == IncludeTokenType.INCLUDE_TOKEN_ALWAYS_TO_INITIATOR) {
                return true;
            }
            return false;
        }
    }
    
    protected Collection<AssertionInfo> getAllAssertionsByLocalname(
        AssertionInfoMap aim,
        String localname
    ) {
        Collection<AssertionInfo> sp11Ais = aim.get(new QName(SP11Constants.SP_NS, localname));
        Collection<AssertionInfo> sp12Ais = aim.get(new QName(SP12Constants.SP_NS, localname));

        if ((sp11Ais != null && !sp11Ais.isEmpty()) || (sp12Ais != null && !sp12Ais.isEmpty())) {
            Collection<AssertionInfo> ais = new HashSet<AssertionInfo>();
            if (sp11Ais != null) {
                ais.addAll(sp11Ais);
            }
            if (sp12Ais != null) {
                ais.addAll(sp12Ais);
            }
            return ais;
        }

        return Collections.emptySet();
    }

    protected Map<String, Object> getProperties() {
        return properties;
    }

    protected SoapMessage getMessage() {
        return message;
    }
    
    protected void configureSignature(
        AbstractTokenWrapper wrapper, AbstractToken token, boolean attached
    ) throws WSSecurityException {
        Map<String, Object> config = getProperties();
        
        if (token instanceof X509Token) {
            X509Token x509Token = (X509Token) token;
            TokenType tokenType = x509Token.getTokenType();
            if (tokenType == TokenType.WssX509PkiPathV1Token10
                || tokenType == TokenType.WssX509PkiPathV1Token11) {
                config.put(ConfigurationConstants.USE_SINGLE_CERTIFICATE, "false");
            }
        }
        
        // boolean alsoIncludeToken = false;
        /* TODO if (token instanceof IssuedToken || token instanceof SamlToken) {
            SecurityToken securityToken = getSecurityToken();
            String tokenType = securityToken.getTokenType();

            Element ref;
            if (attached) {
                ref = securityToken.getAttachedReference();
            } else {
                ref = securityToken.getUnattachedReference();
            }

            if (ref != null) {
                SecurityTokenReference secRef = 
                    new SecurityTokenReference(cloneElement(ref), new BSPEnforcer());
                sig.setSecurityTokenReference(secRef);
                sig.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
            } else {
                int type = attached ? WSConstants.CUSTOM_SYMM_SIGNING 
                    : WSConstants.CUSTOM_SYMM_SIGNING_DIRECT;
                if (WSConstants.WSS_SAML_TOKEN_TYPE.equals(tokenType)
                    || WSConstants.SAML_NS.equals(tokenType)) {
                    sig.setCustomTokenValueType(WSConstants.WSS_SAML_KI_VALUE_TYPE);
                    sig.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                } else if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType)
                    || WSConstants.SAML2_NS.equals(tokenType)) {
                    sig.setCustomTokenValueType(WSConstants.WSS_SAML2_KI_VALUE_TYPE);
                    sig.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                } else {
                    sig.setCustomTokenValueType(tokenType);
                    sig.setKeyIdentifierType(type);
                }
            }

            String sigTokId;
            if (attached) {
                sigTokId = securityToken.getWsuId();
                if (sigTokId == null) {
                    sigTokId = securityToken.getId();                    
                }
                if (sigTokId.startsWith("#")) {
                    sigTokId = sigTokId.substring(1);
                }
            } else {
                sigTokId = securityToken.getId();
            }

            sig.setCustomTokenId(sigTokId);
        } else {
        */
        config.put(ConfigurationConstants.SIG_KEY_ID, getKeyIdentifierType(wrapper, token));
        /*
         * TODO
        // Find out do we also need to include the token as per the Inclusion requirement
        if (token instanceof X509Token 
            && token.getIncludeTokenType() != IncludeTokenType.INCLUDE_TOKEN_NEVER
            && (sig.getKeyIdentifierType() != WSConstants.BST_DIRECT_REFERENCE
            && sig.getKeyIdentifierType() != WSConstants.KEY_VALUE)) {
            alsoIncludeToken = true;
        }
        */
        // }

        AssertionInfoMap aim = message.get(AssertionInfoMap.class);
        AbstractBinding binding = getBinding(aim);
        config.put(ConfigurationConstants.SIG_ALGO, 
                   binding.getAlgorithmSuite().getAsymmetricSignature());
        AlgorithmSuiteType algType = binding.getAlgorithmSuite().getAlgorithmSuiteType();
        config.put(ConfigurationConstants.SIG_DIGEST_ALGO, algType.getDigest());
        // sig.setSigCanonicalization(binding.getAlgorithmSuite().getC14n().getValue());

        //if (alsoIncludeToken) {
        //    includeToken(user, crypto, sig);
        //}
    }
    
    private String getKeyIdentifierType(AbstractTokenWrapper wrapper, AbstractToken token) {

        String identifier = null;
        if (token instanceof X509Token) {
            X509Token x509Token = (X509Token)token;
            if (x509Token.isRequireIssuerSerialReference()) {
                identifier = "IssuerSerial";
            } else if (x509Token.isRequireKeyIdentifierReference()) {
                identifier = "SKIKeyIdentifier";
            } else if (x509Token.isRequireThumbprintReference()) {
                identifier = "Thumbprint";
            }
        } else if (token instanceof KeyValueToken) {
            identifier = "KeyValue";
        }
        
        if (identifier != null) {
            return identifier;
        }

        if (token.getIncludeTokenType() == IncludeTokenType.INCLUDE_TOKEN_NEVER) {
            Wss10 wss = getWss10();
            if (wss == null || wss.isMustSupportRefKeyIdentifier()) {
                return "SKIKeyIdentifier";
            } else if (wss.isMustSupportRefIssuerSerial()) {
                return "IssuerSerial";
            } else if (wss instanceof Wss11
                && ((Wss11) wss).isMustSupportRefThumbprint()) {
                return "Thumbprint";
            }
        } else {
            return "DirectReference";
        }
        
        return "IssuerSerial";
    }
    
    protected Wss10 getWss10() {
        AssertionInfoMap aim = message.get(AssertionInfoMap.class);
        Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, SPConstants.WSS10);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                return (Wss10)ai.getAssertion();
            }            
        }
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.WSS11);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                return (Wss10)ai.getAssertion();
            }            
        }  
        
        return null;
    }
    
    protected Map<AbstractToken, Object> handleSupportingTokens(
        Collection<Assertion> tokens, 
        boolean endorse
    ) throws WSSecurityException {
        Map<AbstractToken, Object> ret = new HashMap<AbstractToken, Object>();
        if (tokens != null) {
            for (Assertion pa : tokens) {
                if (pa instanceof SupportingTokens) {
                    handleSupportingTokens((SupportingTokens)pa, endorse, ret);
                }
            }
        }
        return ret;
    }
                                                            
    protected Map<AbstractToken, Object> handleSupportingTokens(
        SupportingTokens suppTokens,
        boolean endorse
    ) throws WSSecurityException {
        return handleSupportingTokens(suppTokens, endorse, new HashMap<AbstractToken, Object>());
    }
                                                            
    protected Map<AbstractToken, Object> handleSupportingTokens(
        SupportingTokens suppTokens, 
        boolean endorse,
        Map<AbstractToken, Object> ret
    ) throws WSSecurityException {
        if (suppTokens == null) {
            return ret;
        }
        for (AbstractToken token : suppTokens.getTokens()) {
            if (token instanceof UsernameToken) {
                handleUsernameTokenSupportingToken(
                    (UsernameToken)token, endorse, suppTokens.isEncryptedToken(), ret
                );
            /* TODO else if (isRequestor() 
                && (token instanceof IssuedToken
                    || token instanceof SecureConversationToken
                    || token instanceof SecurityContextToken
                    || token instanceof KerberosToken)) {
                //ws-trust/ws-sc stuff.......
                SecurityToken secToken = getSecurityToken();
                if (secToken == null) {
                    policyNotAsserted(token, "Could not find IssuedToken");
                }
                Element clone = cloneElement(secToken.getToken());
                secToken.setToken(clone);
                addSupportingElement(clone);

                String id = secToken.getId();
                if (id != null && id.charAt(0) == '#') {
                    id = id.substring(1);
                }
                if (suppTokens.isEncryptedToken()) {
                    WSEncryptionPart part = new WSEncryptionPart(id, "Element");
                    part.setElement(clone);
                    encryptedTokensList.add(part);
                }

                if (secToken.getX509Certificate() == null) {  
                    ret.put(token, new WSSecurityTokenHolder(wssConfig, secToken));
                } else {
                    WSSecSignature sig = new WSSecSignature(wssConfig);                    
                    sig.setX509Certificate(secToken.getX509Certificate());
                    sig.setCustomTokenId(id);
                    sig.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                    String tokenType = secToken.getTokenType();
                    if (WSConstants.WSS_SAML_TOKEN_TYPE.equals(tokenType)
                        || WSConstants.SAML_NS.equals(tokenType)) {
                        sig.setCustomTokenValueType(WSConstants.WSS_SAML_KI_VALUE_TYPE);
                    } else if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType)
                        || WSConstants.SAML2_NS.equals(tokenType)) {
                        sig.setCustomTokenValueType(WSConstants.WSS_SAML2_KI_VALUE_TYPE);
                    } else if (tokenType != null) {
                        sig.setCustomTokenValueType(tokenType);
                    } else {
                        sig.setCustomTokenValueType(WSConstants.WSS_SAML_KI_VALUE_TYPE);
                    }
                    sig.setSignatureAlgorithm(binding.getAlgorithmSuite().getAsymmetricSignature());
                    sig.setSigCanonicalization(binding.getAlgorithmSuite().getC14n().getValue());

                    Crypto crypto = secToken.getCrypto();
                    String uname = null;
                    try {
                        uname = crypto.getX509Identifier(secToken.getX509Certificate());
                    } catch (WSSecurityException e1) {
                        LOG.log(Level.FINE, e1.getMessage(), e1);
                        throw new Fault(e1);
                    }

                    String password = getPassword(uname, token, WSPasswordCallback.Usage.SIGNATURE);
                    sig.setUserInfo(uname, password);
                    try {
                        sig.prepare(saaj.getSOAPPart(), secToken.getCrypto(), secHeader);
                    } catch (WSSecurityException e) {
                        LOG.log(Level.FINE, e.getMessage(), e);
                        throw new Fault(e);
                    }

                    ret.put(token, sig);                
                }

            } */
            } else if (token instanceof X509Token || token instanceof KeyValueToken) {
                configureSignature(suppTokens, token, false);
                if (suppTokens.isEncryptedToken()) {
                    SecurePart part = 
                        new SecurePart(WSSConstants.TAG_wsse_BinarySecurityToken, Modifier.Element);
                    encryptedTokensList.add(part);
                }
                ret.put(token, new SecurePart(WSSConstants.TAG_dsig_Signature, Modifier.Element));
            } else if (token instanceof SamlToken) {
                SecurePart securePart = addSamlToken((SamlToken)token, false, endorse);
                if (securePart != null) {
                    ret.put(token, securePart);
                    if (suppTokens.isEncryptedToken()) {
                        encryptedTokensList.add(securePart);
                    }
                }
            }
        }
        return ret;
    }

    protected void handleUsernameTokenSupportingToken(
         UsernameToken token, boolean endorse, boolean encryptedToken, Map<AbstractToken, Object> ret
    ) throws WSSecurityException {
        if (endorse) {
            /* TODO
            WSSecUsernameToken utBuilder = addDKUsernameToken(token, true);
            if (utBuilder != null) {
                utBuilder.prepare(saaj.getSOAPPart());
                addSupportingElement(utBuilder.getUsernameTokenElement());
                ret.put(token, utBuilder);
                if (encryptedToken) {
                    WSEncryptionPart part = new WSEncryptionPart(utBuilder.getId(), "Element");
                    part.setElement(utBuilder.getUsernameTokenElement());
                    encryptedTokensList.add(part);
                }
            }
            */
        } else {
            SecurePart securePart = addUsernameToken(token);
            if (securePart != null) {
                ret.put(token, securePart);
                //WebLogic and WCF always encrypt these
                //See:  http://e-docs.bea.com/wls/docs103/webserv_intro/interop.html
                //encryptedTokensIdList.add(utBuilder.getId());
                if (encryptedToken
                    || MessageUtils.getContextualBoolean(message, 
                                                         SecurityConstants.ALWAYS_ENCRYPT_UT,
                                                         true)) {
                    encryptedTokensList.add(securePart);
                }
            }
        }
    }
    
    protected void addSignatureConfirmation(List<SecurePart> sigParts) {
        Wss10 wss10 = getWss10();
        
        if (!(wss10 instanceof Wss11) 
            || !((Wss11)wss10).isRequireSignatureConfirmation()) {
            //If we don't require sig confirmation simply go back :-)
            return;
        }
        
        // Enable SignatureConfirmation
        Map<String, Object> config = getProperties();
        config.put(ConfigurationConstants.ENABLE_SIGNATURE_CONFIRMATION, "true");
        
        if (sigParts != null) {
            SecurePart securePart = 
                new SecurePart(WSSConstants.TAG_wsse11_SignatureConfirmation, Modifier.Element);
            sigParts.add(securePart);
        }
    }
}
