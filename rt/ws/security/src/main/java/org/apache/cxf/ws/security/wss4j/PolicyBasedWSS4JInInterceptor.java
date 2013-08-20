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

package org.apache.cxf.ws.security.wss4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.security.auth.callback.CallbackHandler;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.helpers.MapNamespaceContext;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.CryptoCoverageUtil.CoverageScope;
import org.apache.cxf.ws.security.wss4j.CryptoCoverageUtil.CoverageType;
import org.apache.cxf.ws.security.wss4j.policyvalidators.AsymmetricBindingPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.BindingPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.ConcreteSupportingTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.EncryptedTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.EndorsingEncryptedTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.EndorsingTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.SamlTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.SecurityContextTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.SignedEncryptedTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.SignedEndorsingEncryptedTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.SignedEndorsingTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.SignedTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.SupportingTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.SymmetricBindingPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.TokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.TransportBindingPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.UsernameTokenPolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.WSS11PolicyValidator;
import org.apache.cxf.ws.security.wss4j.policyvalidators.X509TokenPolicyValidator;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.CryptoFactory;
import org.apache.wss4j.common.crypto.PasswordEncryptor;
import org.apache.wss4j.common.crypto.StrongJasyptPasswordEncryptor;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.common.util.Loader;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.WSDataRef;
import org.apache.wss4j.dom.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.RequestData;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.wss4j.dom.message.token.Timestamp;
import org.apache.wss4j.dom.util.WSSecurityUtil;
import org.apache.wss4j.policy.SP11Constants;
import org.apache.wss4j.policy.SP12Constants;
import org.apache.wss4j.policy.SP13Constants;
import org.apache.wss4j.policy.SPConstants;
import org.apache.wss4j.policy.model.AlgorithmSuite;
import org.apache.wss4j.policy.model.Header;
import org.apache.wss4j.policy.model.RequiredElements;
import org.apache.wss4j.policy.model.RequiredParts;
import org.apache.wss4j.policy.model.SignedParts;
import org.apache.wss4j.policy.model.UsernameToken;
import org.apache.wss4j.policy.model.UsernameToken.PasswordType;
import org.apache.wss4j.policy.model.Wss11;

/**
 * 
 */
public class PolicyBasedWSS4JInInterceptor extends WSS4JInInterceptor {
    public static final PolicyBasedWSS4JInInterceptor INSTANCE 
        = new PolicyBasedWSS4JInInterceptor();
    private static final Logger LOG = LogUtils.getL7dLogger(PolicyBasedWSS4JInInterceptor.class);

    /**
     * 
     */
    public PolicyBasedWSS4JInInterceptor() {
        super(true);
    }
    
    public void handleMessage(SoapMessage msg) throws Fault {
        AssertionInfoMap aim = msg.get(AssertionInfoMap.class);
        boolean enableStax = 
            MessageUtils.isTrue(msg.getContextualProperty(SecurityConstants.ENABLE_STREAMING_SECURITY));
        if (aim != null && !enableStax) {
            super.handleMessage(msg);
        }
    }
    
    private static Properties getProps(Object o, URL propsURL, SoapMessage message) {
        Properties properties = null;
        if (o instanceof Properties) {
            properties = (Properties)o;
        } else if (propsURL != null) {
            try {
                properties = new Properties();
                InputStream ins = propsURL.openStream();
                properties.load(ins);
                ins.close();
            } catch (IOException e) {
                properties = null;
            }
        }
        
        return properties;
    }
    
    private URL getPropertiesFileURL(Object o, SoapMessage message) {
        if (o instanceof String) {
            URL url = null;
            ResourceManager rm = message.getExchange().get(Bus.class).getExtension(ResourceManager.class);
            url = rm.resolveResource((String)o, URL.class);
            try {
                if (url == null) {
                    url = ClassLoaderUtils.getResource((String)o, AbstractWSS4JInterceptor.class);
                }
                if (url == null) {
                    url = new URL((String)o);
                }
                return url;
            } catch (IOException e) {
                // Do nothing
            }
        } else if (o instanceof URL) {
            return (URL)o;        
        }
        return null;
    }
    
    private void handleWSS11(AssertionInfoMap aim, SoapMessage message) {
        if (isRequestor(message)) {
            message.put(WSHandlerConstants.ENABLE_SIGNATURE_CONFIRMATION, "false");
            Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, SPConstants.WSS11);
            if (!ais.isEmpty()) {
                for (AssertionInfo ai : ais) {
                    Wss11 wss11 = (Wss11)ai.getAssertion();
                    if (wss11.isRequireSignatureConfirmation()) {
                        message.put(WSHandlerConstants.ENABLE_SIGNATURE_CONFIRMATION, "true");
                        break;
                    }
                }
            }
        }
    }

    private String addToAction(String action, String val, boolean pre) {
        if (action.contains(val)) {
            return action;
        }
        if (pre) {
            return val + " " + action; 
        } 
        return action + " " + val;
    }
    
    private boolean assertPolicy(AssertionInfoMap aim, QName name) {
        Collection<AssertionInfo> ais = aim.getAssertionInfo(name);
        if (ais != null && !ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                ai.setAsserted(true);
            }    
            return true;
        }
        return false;
    }
    
    private boolean assertPolicy(AssertionInfoMap aim, String localname) {
        Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, localname);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                ai.setAsserted(true);
            }    
            return true;
        }
        return false;
    }
    
    private Collection<AssertionInfo> getAllAssertionsByLocalname(
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

    private String checkAsymmetricBinding(
        AssertionInfoMap aim, String action, SoapMessage message, RequestData data
    ) throws WSSecurityException {
        Collection<AssertionInfo> ais = 
            getAllAssertionsByLocalname(aim, SPConstants.ASYMMETRIC_BINDING);
        if (ais.isEmpty()) {
            return action;
        }
        
        action = addToAction(action, "Signature", true);
        action = addToAction(action, "Encrypt", true);
        Object s = message.getContextualProperty(SecurityConstants.SIGNATURE_CRYPTO);
        if (s == null) {
            s = message.getContextualProperty(SecurityConstants.SIGNATURE_PROPERTIES);
        }
        Object e = message.getContextualProperty(SecurityConstants.ENCRYPT_CRYPTO);
        if (e == null) {
            e = message.getContextualProperty(SecurityConstants.ENCRYPT_PROPERTIES);
        }
        
        Crypto encrCrypto = getEncryptionCrypto(e, message, data);
        Crypto signCrypto = null;
        if (e != null && e.equals(s)) {
            signCrypto = encrCrypto;
        } else {
            signCrypto = getSignatureCrypto(s, message, data);
        }
        
        if (signCrypto != null) {
            message.put(WSHandlerConstants.DEC_PROP_REF_ID, "RefId-" + signCrypto.hashCode());
            message.put("RefId-" + signCrypto.hashCode(), signCrypto);
        }
        
        if (encrCrypto != null) {
            message.put(WSHandlerConstants.SIG_VER_PROP_REF_ID, "RefId-" + encrCrypto.hashCode());
            message.put("RefId-" + encrCrypto.hashCode(), (Crypto)encrCrypto);
        } else if (signCrypto != null) {
            message.put(WSHandlerConstants.SIG_VER_PROP_REF_ID, "RefId-" + signCrypto.hashCode());
            message.put("RefId-" + signCrypto.hashCode(), (Crypto)signCrypto);
        }
     
        return action;
    }
    
    private String checkDefaultBinding(
        AssertionInfoMap aim, String action, SoapMessage message, RequestData data
    ) throws WSSecurityException {
        action = addToAction(action, "Signature", true);
        action = addToAction(action, "Encrypt", true);
        Object s = message.getContextualProperty(SecurityConstants.SIGNATURE_CRYPTO);
        if (s == null) {
            s = message.getContextualProperty(SecurityConstants.SIGNATURE_PROPERTIES);
        }
        Object e = message.getContextualProperty(SecurityConstants.ENCRYPT_CRYPTO);
        if (e == null) {
            e = message.getContextualProperty(SecurityConstants.ENCRYPT_PROPERTIES);
        }
        
        Crypto encrCrypto = getEncryptionCrypto(e, message, data);
        Crypto signCrypto = null;
        if (e != null && e.equals(s)) {
            signCrypto = encrCrypto;
        } else {
            signCrypto = getSignatureCrypto(s, message, data);
        }
        
        if (signCrypto != null) {
            message.put(WSHandlerConstants.DEC_PROP_REF_ID, "RefId-" + signCrypto.hashCode());
            message.put("RefId-" + signCrypto.hashCode(), signCrypto);
        }
        
        if (encrCrypto != null) {
            message.put(WSHandlerConstants.SIG_VER_PROP_REF_ID, "RefId-" + encrCrypto.hashCode());
            message.put("RefId-" + encrCrypto.hashCode(), (Crypto)encrCrypto);
        } else if (signCrypto != null) {
            message.put(WSHandlerConstants.SIG_VER_PROP_REF_ID, "RefId-" + signCrypto.hashCode());
            message.put("RefId-" + signCrypto.hashCode(), (Crypto)signCrypto);
        }

        return action;
    }
    
    private void checkUsernameToken(
        AssertionInfoMap aim, SoapMessage message
    ) throws WSSecurityException {
        Collection<AssertionInfo> ais = 
            getAllAssertionsByLocalname(aim, SPConstants.USERNAME_TOKEN);
        
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                UsernameToken policy = (UsernameToken)ai.getAssertion();
                if (policy.getPasswordType() == PasswordType.NoPassword) {
                    message.put(WSHandlerConstants.ALLOW_USERNAMETOKEN_NOPASSWORD, "true");
                }
            }
        }
    }
    
    private String checkSymmetricBinding(
        AssertionInfoMap aim, String action, SoapMessage message, RequestData data
    ) throws WSSecurityException {
        Collection<AssertionInfo> ais = 
            getAllAssertionsByLocalname(aim, SPConstants.SYMMETRIC_BINDING);
        if (ais.isEmpty()) {
            return action;
        }
        
        action = addToAction(action, "Signature", true);
        action = addToAction(action, "Encrypt", true);
        Object s = message.getContextualProperty(SecurityConstants.SIGNATURE_CRYPTO);
        if (s == null) {
            s = message.getContextualProperty(SecurityConstants.SIGNATURE_PROPERTIES);
        }
        Object e = message.getContextualProperty(SecurityConstants.ENCRYPT_CRYPTO);
        if (e == null) {
            e = message.getContextualProperty(SecurityConstants.ENCRYPT_PROPERTIES);
        }
        
        Crypto encrCrypto = getEncryptionCrypto(e, message, data);
        Crypto signCrypto = null;
        if (e != null && e.equals(s)) {
            signCrypto = encrCrypto;
        } else {
            signCrypto = getSignatureCrypto(s, message, data);
        }
        
        if (isRequestor(message)) {
            Crypto crypto = encrCrypto;
            if (crypto == null) {
                crypto = signCrypto;
            }
            if (crypto != null) {
                message.put(WSHandlerConstants.SIG_VER_PROP_REF_ID, "RefId-" + crypto.hashCode());
                message.put("RefId-" + crypto.hashCode(), crypto);
            }
            
            crypto = signCrypto;
            if (crypto == null) {
                crypto = encrCrypto;
            }
            if (crypto != null) {
                message.put(WSHandlerConstants.DEC_PROP_REF_ID, "RefId-" + crypto.hashCode());
                message.put("RefId-" + crypto.hashCode(), crypto);
            }
        } else {
            Crypto crypto = signCrypto;
            if (crypto == null) {
                crypto = encrCrypto;
            }
            if (crypto != null) {
                message.put(WSHandlerConstants.SIG_VER_PROP_REF_ID, "RefId-" + crypto.hashCode());
                message.put("RefId-" + crypto.hashCode(), crypto);
            }
            
            crypto = encrCrypto;
            if (crypto == null) {
                crypto = signCrypto;
            }
            if (crypto != null) {
                message.put(WSHandlerConstants.DEC_PROP_REF_ID, "RefId-" + crypto.hashCode());
                message.put("RefId-" + crypto.hashCode(), crypto);
            }
        }
        
        return action;
    }
    
    private Crypto getEncryptionCrypto(Object e, 
                                       SoapMessage message, 
                                       RequestData requestData) throws WSSecurityException {
        Crypto encrCrypto = null;
        if (e instanceof Crypto) {
            encrCrypto = (Crypto)e;
        } else if (e != null) {
            URL propsURL = getPropertiesFileURL(e, message);
            Properties props = getProps(e, propsURL, message);
            if (props == null) {
                LOG.fine("Cannot find Crypto Encryption properties: " + e);
                Exception ex = new Exception("Cannot find Crypto Encryption properties: " + e);
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE, ex);
            }
            
            PasswordEncryptor passwordEncryptor = getPasswordEncryptor(message, requestData);
            encrCrypto = CryptoFactory.getInstance(props, Loader.getClassLoader(CryptoFactory.class),
                                                   passwordEncryptor);

            EndpointInfo info = message.getExchange().get(Endpoint.class).getEndpointInfo();
            synchronized (info) {
                info.setProperty(SecurityConstants.ENCRYPT_CRYPTO, encrCrypto);
            }
        }
        return encrCrypto;
    }
    
    private PasswordEncryptor getPasswordEncryptor(SoapMessage soapMessage, RequestData requestData) {
        PasswordEncryptor passwordEncryptor = 
            (PasswordEncryptor)soapMessage.getContextualProperty(
                SecurityConstants.PASSWORD_ENCRYPTOR_INSTANCE
            );
        if (passwordEncryptor != null) {
            return passwordEncryptor;
        }
        
        if (requestData.getPasswordEncryptor() != null) {
            return requestData.getPasswordEncryptor();
        }
        
        CallbackHandler callbackHandler = requestData.getCallbackHandler();
        if (callbackHandler != null) {
            return new StrongJasyptPasswordEncryptor(callbackHandler);
        }
        
        return null;
    }
    
    private Crypto getSignatureCrypto(Object s, SoapMessage message, 
                                      RequestData requestData) throws WSSecurityException {
        Crypto signCrypto = null;
        if (s instanceof Crypto) {
            signCrypto = (Crypto)s;
        } else if (s != null) {
            URL propsURL = getPropertiesFileURL(s, message);
            Properties props = getProps(s, propsURL, message);
            if (props == null) {
                LOG.fine("Cannot find Crypto Signature properties: " + s);
                Exception ex = new Exception("Cannot find Crypto Signature properties: " + s);
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE, ex);
            }
            
            PasswordEncryptor passwordEncryptor = getPasswordEncryptor(message, requestData);
            signCrypto = CryptoFactory.getInstance(props, Loader.getClassLoader(CryptoFactory.class),
                                                   passwordEncryptor);

            EndpointInfo info = message.getExchange().get(Endpoint.class).getEndpointInfo();
            synchronized (info) {
                info.setProperty(SecurityConstants.SIGNATURE_CRYPTO, signCrypto);
            }
        }
        return signCrypto;
    }
    
    private boolean assertXPathTokens(AssertionInfoMap aim, 
                                   String name, 
                                   Collection<WSDataRef> refs,
                                   Element soapEnvelope,
                                   CoverageType type,
                                   CoverageScope scope,
                                   final XPath xpath) throws SOAPException {
        Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, name);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                ai.setAsserted(true);
                
                RequiredElements elements = (RequiredElements)ai.getAssertion();
                
                if (elements != null && elements.getXPaths() != null 
                    && !elements.getXPaths().isEmpty()) {
                    List<String> expressions = new ArrayList<String>();
                    for (org.apache.wss4j.policy.model.XPath xPath : elements.getXPaths()) {
                        expressions.add(xPath.getXPath());
                    }

                    if (elements.getXPaths().get(0).getPrefixNamespaceMap() != null) {
                        xpath.setNamespaceContext(
                            new MapNamespaceContext(elements.getXPaths().get(0).getPrefixNamespaceMap())
                        );
                    }
                    try {
                        CryptoCoverageUtil.checkCoverage(soapEnvelope, refs,
                                                         xpath, expressions, type, scope);
                    } catch (WSSecurityException e) {
                        ai.setNotAsserted("No " + type 
                                          + " element found matching one of the XPaths " 
                                          + Arrays.toString(expressions.toArray()));
                    }
                }
            }
        }
        return true;
    }

    
    private boolean assertTokens(AssertionInfoMap aim, 
                              String name, 
                              Collection<WSDataRef> signed,
                              SoapMessage msg,
                              Element soapHeader,
                              Element soapBody,
                              CoverageType type) throws SOAPException {
        Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, name);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                ai.setAsserted(true);
                SignedParts p = (SignedParts)ai.getAssertion();
                
                if (p.isBody()) {
                    try {
                        if (CoverageType.SIGNED.equals(type)) {
                            CryptoCoverageUtil.checkBodyCoverage(
                                soapBody, signed, type, CoverageScope.ELEMENT
                            );
                        } else {
                            CryptoCoverageUtil.checkBodyCoverage(
                                soapBody, signed, type, CoverageScope.CONTENT
                            );
                        }
                    } catch (WSSecurityException e) {
                        ai.setNotAsserted(msg.getVersion().getBody() + " not " + type);
                        continue;
                    }
                }
                
                for (Header h : p.getHeaders()) {
                    try {
                        CryptoCoverageUtil.checkHeaderCoverage(soapHeader, signed, h
                                .getNamespace(), h.getName(), type,
                                CoverageScope.ELEMENT);
                    } catch (WSSecurityException e) {
                        ai.setNotAsserted(h.getNamespace() + ":" + h.getName() + " not + " + type);
                    }
                }
            }
        }
        return true;
    }
    
    
    /**
     * Set a WSS4J AlgorithmSuite object on the RequestData context, to restrict the
     * algorithms that are allowed for encryption, signature, etc.
     */
    protected void setAlgorithmSuites(SoapMessage message, RequestData data) throws WSSecurityException {
        AlgorithmSuiteTranslater translater = new AlgorithmSuiteTranslater();
        translater.translateAlgorithmSuites(message.get(AssertionInfoMap.class), data);
        
        // Allow for setting non-standard asymmetric signature algorithms
        String asymSignatureAlgorithm = 
            (String)message.getContextualProperty(SecurityConstants.ASYMMETRIC_SIGNATURE_ALGORITHM);
        if (asymSignatureAlgorithm != null && data.getAlgorithmSuite() != null) {
            data.getAlgorithmSuite().getSignatureMethods().clear();
            data.getAlgorithmSuite().getSignatureMethods().add(asymSignatureAlgorithm);
        }
    }

    protected void computeAction(SoapMessage message, RequestData data) throws WSSecurityException {
        String action = getString(WSHandlerConstants.ACTION, message);
        if (action == null) {
            action = "";
        }
        AssertionInfoMap aim = message.get(AssertionInfoMap.class);
        if (aim != null) {
            //things that DO impact setup
            handleWSS11(aim, message);
            action = checkAsymmetricBinding(aim, action, message, data);
            action = checkSymmetricBinding(aim, action, message, data);
            Collection<AssertionInfo> ais = aim.get(SP12Constants.TRANSPORT_BINDING);
            if ("".equals(action) || (ais != null && !ais.isEmpty())) {
                action = checkDefaultBinding(aim, action, message, data);
            }
            
            // Allow for setting non-standard asymmetric signature algorithms
            String asymSignatureAlgorithm = 
                (String)message.getContextualProperty(SecurityConstants.ASYMMETRIC_SIGNATURE_ALGORITHM);
            if (asymSignatureAlgorithm != null) {
                Collection<AssertionInfo> algorithmSuites = 
                    aim.get(SP12Constants.ALGORITHM_SUITE);
                if (algorithmSuites != null && !algorithmSuites.isEmpty()) {
                    for (AssertionInfo algorithmSuite : algorithmSuites) {
                        AlgorithmSuite algSuite = (AlgorithmSuite)algorithmSuite.getAssertion();
                        algSuite.setAsymmetricSignature(asymSignatureAlgorithm);
                    }
                }
            }
            
            checkUsernameToken(aim, message);
            
            // stuff we can default to asserted and un-assert if a condition isn't met
            assertPolicy(aim, SPConstants.KEY_VALUE_TOKEN);
            assertPolicy(aim, SPConstants.RSA_KEY_VALUE);
            assertPolicy(aim, SPConstants.REQUIRE_ISSUER_SERIAL_REFERENCE);
            assertPolicy(aim, SPConstants.REQUIRE_THUMBPRINT_REFERENCE);
            assertPolicy(aim, SPConstants.REQUIRE_KEY_IDENTIFIER_REFERENCE);
            assertPolicy(aim, SPConstants.REQUIRE_EMBEDDED_TOKEN_REFERENCE);
            assertPolicy(aim, SPConstants.REQUIRE_INTERNAL_REFERENCE);
            
            // WSS10
            assertPolicy(aim, SPConstants.WSS10);
            assertPolicy(aim, SPConstants.MUST_SUPPORT_REF_KEY_IDENTIFIER);
            assertPolicy(aim, SPConstants.MUST_SUPPORT_REF_ISSUER_SERIAL);
            assertPolicy(aim, SPConstants.MUST_SUPPORT_REF_EXTERNAL_URI);
            assertPolicy(aim, SPConstants.MUST_SUPPORT_REF_EMBEDDED_TOKEN);
            
            // Trust 1.0
            assertPolicy(aim, SPConstants.TRUST_10);
            assertPolicy(aim, SPConstants.MUST_SUPPORT_CLIENT_CHALLENGE);
            assertPolicy(aim, SPConstants.MUST_SUPPORT_SERVER_CHALLENGE);
            assertPolicy(aim, SPConstants.REQUIRE_CLIENT_ENTROPY);
            assertPolicy(aim, SPConstants.REQUIRE_SERVER_ENTROPY);
            assertPolicy(aim, SPConstants.MUST_SUPPORT_ISSUED_TOKENS);
            
            // Trust 1.3
            assertPolicy(aim, SPConstants.TRUST_13);
            assertPolicy(aim, SP12Constants.REQUIRE_REQUEST_SECURITY_TOKEN_COLLECTION);
            assertPolicy(aim, SP12Constants.REQUIRE_APPLIES_TO);
            assertPolicy(aim, SP13Constants.SCOPE_POLICY_15);
            assertPolicy(aim, SP13Constants.MUST_SUPPORT_INTERACTIVE_CHALLENGE);
            
            message.put(WSHandlerConstants.ACTION, action.trim());
        }
    }
    
    @Override
    protected void doResults(
        SoapMessage msg, 
        String actor,
        Element soapHeader,
        Element soapBody,
        List<WSSecurityEngineResult> results, 
        boolean utWithCallbacks
    ) throws SOAPException, XMLStreamException, WSSecurityException {
        AssertionInfoMap aim = msg.get(AssertionInfoMap.class);
        Collection<WSDataRef> signed = new HashSet<WSDataRef>();
        Collection<WSDataRef> encrypted = new HashSet<WSDataRef>();
        
        //
        // Pre-fetch various results
        //
        final List<Integer> actions = new ArrayList<Integer>(2);
        actions.add(WSConstants.SIGN);
        actions.add(WSConstants.UT_SIGN);
        List<WSSecurityEngineResult> signedResults = 
            WSSecurityUtil.fetchAllActionResults(results, actions);
        for (WSSecurityEngineResult result : signedResults) {
            List<WSDataRef> sl = 
                CastUtils.cast((List<?>)result.get(WSSecurityEngineResult.TAG_DATA_REF_URIS));
            if (sl != null) {
                for (WSDataRef r : sl) {
                    signed.add(r);
                }
            }
        }
        
        List<WSSecurityEngineResult> encryptResults = 
            WSSecurityUtil.fetchAllActionResults(results, WSConstants.ENCR);
        for (WSSecurityEngineResult result : encryptResults) {
            List<WSDataRef> sl = 
                CastUtils.cast((List<?>)result.get(WSSecurityEngineResult.TAG_DATA_REF_URIS));
            if (sl != null) {
                for (WSDataRef r : sl) {
                    encrypted.add(r);
                }
            }
        }
        
        //
        // Check policies
        //
        if (!checkSignedEncryptedCoverage(aim, msg, soapHeader, soapBody, signed, encrypted)) {
            LOG.fine("Incoming request failed signed-encrypted policy validation");
        }
        
        if (!checkTokenCoverage(aim, msg, soapBody, results, signedResults)) {
            LOG.fine("Incoming request failed token policy validation");
        }
        
        if (!checkBindingCoverage(aim, msg, soapBody, results, signedResults, encryptResults)) {
            LOG.fine("Incoming request failed binding policy validation");
        }

        if (!checkSupportingTokenCoverage(aim, msg, results, signedResults, 
            encryptResults, utWithCallbacks)) {
            LOG.fine("Incoming request failed supporting token policy validation");
        }
        
        super.doResults(msg, actor, soapHeader, soapBody, results, utWithCallbacks);
    }
    
    /**
     * Check SignedParts, EncryptedParts, SignedElements, EncryptedElements, RequiredParts, etc.
     */
    private boolean checkSignedEncryptedCoverage(
        AssertionInfoMap aim,
        SoapMessage msg,
        Element soapHeader,
        Element soapBody,
        Collection<WSDataRef> signed, 
        Collection<WSDataRef> encrypted
    ) throws SOAPException {
        CryptoCoverageUtil.reconcileEncryptedSignedRefs(signed, encrypted);
        //
        // SIGNED_PARTS and ENCRYPTED_PARTS only apply to non-Transport bindings
        //
        boolean check = true;
        if (!isTransportBinding(aim)) {
            check &= assertTokens(
                aim, SPConstants.SIGNED_PARTS, signed, msg, soapHeader, soapBody, CoverageType.SIGNED
            );
            check &= assertTokens(
                aim, SPConstants.ENCRYPTED_PARTS, encrypted, msg, soapHeader, soapBody, 
                CoverageType.ENCRYPTED
            );
        }
        Element soapEnvelope = soapHeader.getOwnerDocument().getDocumentElement();
        if (containsXPathPolicy(aim)) {
            // XPathFactory and XPath are not thread-safe so we must recreate them
            // each request.
            final XPathFactory factory = XPathFactory.newInstance();
            final XPath xpath = factory.newXPath();
            
            check &= assertXPathTokens(aim, SPConstants.SIGNED_ELEMENTS, signed, soapEnvelope,
                    CoverageType.SIGNED, CoverageScope.ELEMENT, xpath);
            check &= assertXPathTokens(aim, SPConstants.ENCRYPTED_ELEMENTS, encrypted, soapEnvelope,
                    CoverageType.ENCRYPTED, CoverageScope.ELEMENT, xpath);
            check &= assertXPathTokens(aim, SPConstants.CONTENT_ENCRYPTED_ELEMENTS, encrypted, 
                    soapEnvelope, CoverageType.ENCRYPTED, CoverageScope.CONTENT, xpath);
        }
        
        check &= assertHeadersExists(aim, msg, soapHeader);
        return check;
    }
    
    /**
     * Check the token coverage
     */
    private boolean checkTokenCoverage(
        AssertionInfoMap aim,
        SoapMessage msg,
        Element soapBody,
        List<WSSecurityEngineResult> results, 
        List<WSSecurityEngineResult> signedResults
    ) {
        boolean check = true;
        TokenPolicyValidator x509Validator = new X509TokenPolicyValidator();
        check &= x509Validator.validatePolicy(aim, msg, soapBody, results, signedResults);
        
        TokenPolicyValidator utValidator = new UsernameTokenPolicyValidator();
        check &= utValidator.validatePolicy(aim, msg, soapBody, results, signedResults);
        
        TokenPolicyValidator samlValidator = new SamlTokenPolicyValidator();
        check &= samlValidator.validatePolicy(aim, msg, soapBody, results, signedResults);
        
        TokenPolicyValidator sctValidator = new SecurityContextTokenPolicyValidator();
        check &= sctValidator.validatePolicy(aim, msg, soapBody, results, signedResults);
        
        TokenPolicyValidator wss11Validator = new WSS11PolicyValidator();
        check &= wss11Validator.validatePolicy(aim, msg, soapBody, results, signedResults);
        
        return check;
    }
    
    /**
     * Check the binding coverage
     */
    private boolean checkBindingCoverage(
        AssertionInfoMap aim, 
        SoapMessage msg,
        Element soapBody,
        List<WSSecurityEngineResult> results,
        List<WSSecurityEngineResult> signedResults,
        List<WSSecurityEngineResult> encryptedResults
    ) {
        boolean check = true;
        
        BindingPolicyValidator transportValidator = new TransportBindingPolicyValidator();
        check &= 
            transportValidator.validatePolicy(
                aim, msg, soapBody, results, signedResults, encryptedResults
            );
            
        BindingPolicyValidator symmetricValidator = new SymmetricBindingPolicyValidator();
        check &= 
            symmetricValidator.validatePolicy(
                aim, msg, soapBody, results, signedResults, encryptedResults
            );

        BindingPolicyValidator asymmetricValidator = new AsymmetricBindingPolicyValidator();
        check &= 
            asymmetricValidator.validatePolicy(
                aim, msg, soapBody, results, signedResults, encryptedResults
            );
        
        return check;
    }
    
    /**
     * Check the supporting token coverage
     */
    private boolean checkSupportingTokenCoverage(
        AssertionInfoMap aim,
        SoapMessage msg,
        List<WSSecurityEngineResult> results, 
        List<WSSecurityEngineResult> signedResults,
        List<WSSecurityEngineResult> encryptedResults,
        boolean utWithCallbacks
    ) {
        final List<Integer> utActions = new ArrayList<Integer>(2);
        utActions.add(WSConstants.UT);
        utActions.add(WSConstants.UT_NOPASSWORD);
        List<WSSecurityEngineResult> utResults = 
            WSSecurityUtil.fetchAllActionResults(results, utActions);
        
        final List<Integer> samlActions = new ArrayList<Integer>(2);
        samlActions.add(WSConstants.ST_SIGNED);
        samlActions.add(WSConstants.ST_UNSIGNED);
        List<WSSecurityEngineResult> samlResults = 
            WSSecurityUtil.fetchAllActionResults(results, samlActions);
        
        // Store the timestamp element
        WSSecurityEngineResult tsResult = WSSecurityUtil.fetchActionResult(results, WSConstants.TS);
        Element timestamp = null;
        if (tsResult != null) {
            Timestamp ts = (Timestamp)tsResult.get(WSSecurityEngineResult.TAG_TIMESTAMP);
            timestamp = ts.getElement();
        }
        
        boolean check = true;
        
        SupportingTokenPolicyValidator validator = new ConcreteSupportingTokenPolicyValidator();
        validator.setUsernameTokenResults(utResults, utWithCallbacks);
        validator.setSAMLTokenResults(samlResults);
        validator.setTimestampElement(timestamp);
        check &= validator.validatePolicy(aim, msg, results, signedResults, encryptedResults);
        
        validator = new SignedTokenPolicyValidator();
        validator.setUsernameTokenResults(utResults, utWithCallbacks);
        validator.setSAMLTokenResults(samlResults);
        validator.setTimestampElement(timestamp);
        check &= validator.validatePolicy(aim, msg, results, signedResults, encryptedResults);

        validator = new EndorsingTokenPolicyValidator();
        validator.setUsernameTokenResults(utResults, utWithCallbacks);
        validator.setSAMLTokenResults(samlResults);
        validator.setTimestampElement(timestamp);
        check &= validator.validatePolicy(aim, msg, results, signedResults, encryptedResults);

        validator = new SignedEndorsingTokenPolicyValidator();
        validator.setUsernameTokenResults(utResults, utWithCallbacks);
        validator.setSAMLTokenResults(samlResults);
        validator.setTimestampElement(timestamp);
        check &= validator.validatePolicy(aim, msg, results, signedResults, encryptedResults);

        validator = new SignedEncryptedTokenPolicyValidator();
        validator.setUsernameTokenResults(utResults, utWithCallbacks);
        validator.setSAMLTokenResults(samlResults);
        validator.setTimestampElement(timestamp);
        check &= validator.validatePolicy(aim, msg, results, signedResults, encryptedResults);

        validator = new EncryptedTokenPolicyValidator();
        validator.setUsernameTokenResults(utResults, utWithCallbacks);
        validator.setSAMLTokenResults(samlResults);
        validator.setTimestampElement(timestamp);
        check &= validator.validatePolicy(aim, msg, results, signedResults, encryptedResults);

        validator = new EndorsingEncryptedTokenPolicyValidator();
        validator.setUsernameTokenResults(utResults, utWithCallbacks);
        validator.setSAMLTokenResults(samlResults);
        validator.setTimestampElement(timestamp);
        check &= validator.validatePolicy(aim, msg, results, signedResults, encryptedResults);

        validator = new SignedEndorsingEncryptedTokenPolicyValidator();
        validator.setUsernameTokenResults(utResults, utWithCallbacks);
        validator.setSAMLTokenResults(samlResults);
        validator.setTimestampElement(timestamp);
        check &= validator.validatePolicy(aim, msg, results, signedResults, encryptedResults);
        
        return check;
    }
    
    private boolean assertHeadersExists(AssertionInfoMap aim, SoapMessage msg, Node header) 
        throws SOAPException {
        
        Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, SPConstants.REQUIRED_PARTS);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                RequiredParts rp = (RequiredParts)ai.getAssertion();
                ai.setAsserted(true);
                for (Header h : rp.getHeaders()) {
                    QName qName = new QName(h.getNamespace(), h.getName());
                    if (header == null 
                        || DOMUtils.getFirstChildWithName((Element)header, qName) == null) {
                        ai.setNotAsserted("No header element of name " + qName + " found.");
                    }
                }
            }
        }
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.REQUIRED_ELEMENTS);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                RequiredElements rp = (RequiredElements)ai.getAssertion();
                ai.setAsserted(true);
                
                if (rp != null && rp.getXPaths() != null && !rp.getXPaths().isEmpty()) {
                    XPathFactory factory = XPathFactory.newInstance();
                    for (org.apache.wss4j.policy.model.XPath xPath : rp.getXPaths()) {
                        Map<String, String> namespaces = xPath.getPrefixNamespaceMap();
                        String expression = xPath.getXPath();
    
                        XPath xpath = factory.newXPath();
                        if (namespaces != null) {
                            xpath.setNamespaceContext(new MapNamespaceContext(namespaces));
                        }
                        NodeList list;
                        try {
                            list = (NodeList)xpath.evaluate(expression, 
                                                                     header,
                                                                     XPathConstants.NODESET);
                            if (list.getLength() == 0) {
                                ai.setNotAsserted("No header element matching XPath " + expression + " found.");
                            }
                        } catch (XPathExpressionException e) {
                            ai.setNotAsserted("Invalid XPath expression " + expression + " " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        return true;
    }

    private boolean isTransportBinding(AssertionInfoMap aim) {
        Collection<AssertionInfo> ais = 
            getAllAssertionsByLocalname(aim, SPConstants.TRANSPORT_BINDING);
        if (ais.size() > 0) {
            ais = getAllAssertionsByLocalname(aim, SPConstants.SYMMETRIC_BINDING);
            if (ais.size() > 0) {
                return false;
            }
            ais = getAllAssertionsByLocalname(aim, SPConstants.ASYMMETRIC_BINDING);
            if (ais.size() > 0) {
                return false;
            }
            return true;
        }
        return false;
    }
    
    private boolean containsXPathPolicy(AssertionInfoMap aim) {
        Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, SPConstants.SIGNED_ELEMENTS);
        if (ais.size() > 0) {
            return true;
        }
        ais = getAllAssertionsByLocalname(aim, SPConstants.ENCRYPTED_ELEMENTS);
        if (ais.size() > 0) {
            return true;
        }
        ais = getAllAssertionsByLocalname(aim, SPConstants.CONTENT_ENCRYPTED_ELEMENTS);
        if (ais.size() > 0) {
            return true;
        }
        return false;
    }

}
