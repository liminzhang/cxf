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

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.common.security.SimplePrincipal;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.security.DefaultSecurityContext;
import org.apache.cxf.interceptor.security.RolePrefixSecurityContextImpl;
import org.apache.cxf.interceptor.security.SAMLSecurityContext;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.security.SecurityContext;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.common.principal.CustomTokenPrincipal;
import org.apache.wss4j.common.principal.WSDerivedKeyTokenPrincipal;
import org.apache.wss4j.stax.securityEvent.KerberosTokenSecurityEvent;
import org.apache.wss4j.stax.securityEvent.KeyValueTokenSecurityEvent;
import org.apache.wss4j.stax.securityEvent.SamlTokenSecurityEvent;
import org.apache.wss4j.stax.securityEvent.UsernameTokenSecurityEvent;
import org.apache.wss4j.stax.securityEvent.WSSecurityEventConstants;
import org.apache.wss4j.stax.securityEvent.X509TokenSecurityEvent;
import org.apache.wss4j.stax.securityToken.SubjectAndPrincipalSecurityToken;
import org.apache.xml.security.stax.securityEvent.SecurityEvent;

/**
 * This interceptor handles parsing the StaX WS-Security results (events) + sets up the
 * security context appropriately.
 */
public class StaxSecurityContextInInterceptor extends AbstractPhaseInterceptor<SoapMessage> {
    
    /**
     * This configuration tag specifies the default attribute name where the roles are present
     * The default is "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role".
     */
    public static final String SAML_ROLE_ATTRIBUTENAME_DEFAULT =
        "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role";
    
    public StaxSecurityContextInInterceptor() {
        super(Phase.PRE_PROTOCOL);
    }

    @Override
    public void handleMessage(SoapMessage soapMessage) throws Fault {
        
        @SuppressWarnings("unchecked")
        final List<SecurityEvent> incomingSecurityEventList = 
            (List<SecurityEvent>)soapMessage.get(SecurityEvent.class.getName() + ".in");

        if (incomingSecurityEventList != null) {
            try {
                doResults(soapMessage, incomingSecurityEventList);
            } catch (WSSecurityException e) {
                throw createSoapFault(soapMessage.getVersion(), e);
            }
        }
    }
    
    private void doResults(SoapMessage msg, List<SecurityEvent> incomingSecurityEventList) throws WSSecurityException {
        for (SecurityEvent event : incomingSecurityEventList) {
            SubjectAndPrincipalSecurityToken token = getSubjectPrincipalToken(event);
            if (token != null) {
                Principal p = token.getPrincipal();
                Subject subject = token.getSubject();
                
                if (subject != null) {
                    String roleClassifier = 
                        (String)msg.getContextualProperty(SecurityConstants.SUBJECT_ROLE_CLASSIFIER);
                    if (roleClassifier != null && !"".equals(roleClassifier)) {
                        String roleClassifierType = 
                            (String)msg.getContextualProperty(SecurityConstants.SUBJECT_ROLE_CLASSIFIER_TYPE);
                        if (roleClassifierType == null || "".equals(roleClassifierType)) {
                            roleClassifierType = "prefix";
                        }
                        msg.put(
                            SecurityContext.class, 
                            new RolePrefixSecurityContextImpl(subject, roleClassifier, roleClassifierType)
                        );
                    } else {
                        msg.put(SecurityContext.class, new DefaultSecurityContext(subject));
                    }
                    break;
                } else if (p != null && isSecurityContextPrincipal(p, incomingSecurityEventList)) {

                    Object receivedAssertion = null;
                    
                    List<String> roles = null;
                    if (event.getSecurityEventType() == WSSecurityEventConstants.SamlToken) {
                        String roleAttributeName = (String)msg.getContextualProperty(
                                SecurityConstants.SAML_ROLE_ATTRIBUTENAME);
                        if (roleAttributeName == null || roleAttributeName.length() == 0) {
                            roleAttributeName = SAML_ROLE_ATTRIBUTENAME_DEFAULT;
                        }
                        
                        SamlTokenSecurityEvent samlEvent = (SamlTokenSecurityEvent)event;
                        receivedAssertion = samlEvent.getSamlAssertionWrapper();
                        if (receivedAssertion != null) {
                            roles = SAMLUtils.parseRolesInAssertion(receivedAssertion, roleAttributeName);
                            SAMLSecurityContext context = createSecurityContext(p, roles);
                            context.setIssuer(SAMLUtils.getIssuer(receivedAssertion));
                            context.setAssertionElement(SAMLUtils.getAssertionElement(receivedAssertion));
                            msg.put(SecurityContext.class, context);
                        }
                    } else {
                        msg.put(SecurityContext.class, createSecurityContext(p));
                    }
                    break;
                }
            }
        }
    }
    

    /**
     * Checks if a given WSS4J Principal can be represented as a user principal
     * inside SecurityContext. Example, UsernameToken or PublicKey principals can
     * be used to facilitate checking the user roles, etc.
     */
    private boolean isSecurityContextPrincipal(Principal p, List<SecurityEvent> incomingSecurityEventList) {
        
        boolean derivedKeyPrincipal = p instanceof WSDerivedKeyTokenPrincipal;
        if (derivedKeyPrincipal || p instanceof CustomTokenPrincipal) {
            // If it is a derived key principal or a Custom Token Principal then let it 
            // be a SecurityContext principal only if no other principals are available.
            return incomingSecurityEventList.size() > 1 ? false : true;
        } else {
            return true;
        }
    }
    
    private SubjectAndPrincipalSecurityToken getSubjectPrincipalToken(SecurityEvent event) {
        if (event.getSecurityEventType() == WSSecurityEventConstants.UsernameToken) {
            return ((UsernameTokenSecurityEvent)event).getSecurityToken();
        } else if (event.getSecurityEventType() == WSSecurityEventConstants.SamlToken) {
            return ((SamlTokenSecurityEvent)event).getSecurityToken();
        } else if (event.getSecurityEventType() == WSSecurityEventConstants.X509Token) {
            return ((X509TokenSecurityEvent)event).getSecurityToken();
        } else if (event.getSecurityEventType() == WSSecurityEventConstants.KeyValueToken) {
            return ((KeyValueTokenSecurityEvent)event).getSecurityToken();
        } else if (event.getSecurityEventType() == WSSecurityEventConstants.KerberosToken) {
            return ((KerberosTokenSecurityEvent)event).getSecurityToken();
        }
        return null;
    }
    
    private SecurityContext createSecurityContext(final Principal p) {
        return new SecurityContext() {

            public Principal getUserPrincipal() {
                return p;
            }

            public boolean isUserInRole(String arg0) {
                return false;
            }
        };
    }
    
    private SAMLSecurityContext createSecurityContext(final Principal p, final List<String> roles) {
        final Set<Principal> userRoles;
        if (roles != null) {
            userRoles = new HashSet<Principal>();
            for (String role : roles) {
                userRoles.add(new SimplePrincipal(role));
            }
        } else {
            userRoles = null;
        }
        
        return new SAMLSecurityContext(p, userRoles);
    }
    
    /**
     * Create a SoapFault from a WSSecurityException, following the SOAP Message Security
     * 1.1 specification, chapter 12 "Error Handling".
     * 
     * When the Soap version is 1.1 then set the Fault/Code/Value from the fault code
     * specified in the WSSecurityException (if it exists).
     * 
     * Otherwise set the Fault/Code/Value to env:Sender and the Fault/Code/Subcode/Value
     * as the fault code from the WSSecurityException.
     */
    private SoapFault 
    createSoapFault(SoapVersion version, WSSecurityException e) {
        SoapFault fault;
        javax.xml.namespace.QName faultCode = e.getFaultCode();
        if (version.getVersion() == 1.1 && faultCode != null) {
            fault = new SoapFault(e.getMessage(), e, faultCode);
        } else {
            fault = new SoapFault(e.getMessage(), e, version.getSender());
            if (version.getVersion() != 1.1 && faultCode != null) {
                fault.setSubCode(faultCode);
            }
        }
        return fault;
    }
}
