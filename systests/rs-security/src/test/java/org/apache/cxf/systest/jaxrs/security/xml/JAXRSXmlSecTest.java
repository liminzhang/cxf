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

package org.apache.cxf.systest.jaxrs.security.xml;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.rs.security.common.SecurityUtils;
import org.apache.cxf.rs.security.xml.XmlEncInInterceptor;
import org.apache.cxf.rs.security.xml.XmlEncOutInterceptor;
import org.apache.cxf.rs.security.xml.XmlSigInInterceptor;
import org.apache.cxf.rs.security.xml.XmlSigOutInterceptor;
import org.apache.cxf.systest.jaxrs.security.Book;
import org.apache.cxf.systest.jaxrs.security.BookStore;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.xml.security.encryption.XMLCipher;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;

@RunWith(value = org.junit.runners.Parameterized.class)
public class JAXRSXmlSecTest extends AbstractBusClientServerTestBase {
    public static final String PORT = BookServerXmlSec.PORT;
    public static final String STAX_PORT = StaxBookServerXmlSec.PORT;
    
    final TestParam test;
    
    public JAXRSXmlSecTest(TestParam type) {
        this.test = type;
    }

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue("server did not launch correctly", 
                   launchServer(BookServerXmlSec.class, true));
        assertTrue("server did not launch correctly", 
                   launchServer(StaxBookServerXmlSec.class, true));
    }
    
    @Parameters(name = "{0}")
    public static Collection<TestParam[]> data() {
       
        return Arrays.asList(new TestParam[][] {{new TestParam(PORT, false)},
                                                {new TestParam(STAX_PORT, false)},
        });
    }
    
    @Test
    public void testPostBookWithEnvelopedSigAndProxy() throws Exception {
        String address = "https://localhost:" + test.port + "/xmlsig";
        doTestSignatureProxy(address, false, null);
    }
    
    @Test
    public void testPostBookWithEnvelopedSigAndProxy2() throws Exception {
        String address = "https://localhost:" + test.port + "/xmlsig";
        doTestSignatureProxy(address, false, "");
    }
    
    @Test
    public void testPostBookEnvelopingSigAndProxy() throws Exception {
        if (STAX_PORT.equals(test.port)) {
            // TODO Supporting Enveloping
            return;
        }
        String address = "https://localhost:" + test.port + "/xmlsig";
        doTestSignatureProxy(address, true, "file:");
    }
    
    private void doTestSignatureProxy(String address, boolean enveloping, String cryptoUrlPrefix) throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress(address);
        
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JAXRSXmlSecTest.class.getResource("client.xml");
        Bus springBus = bf.createBus(busFile.toString());
        bean.setBus(springBus);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.signature.username", "alice");
        
        String cryptoUrl = "org/apache/cxf/systest/jaxrs/security/alice.properties";
        if (cryptoUrlPrefix != null) {
            cryptoUrl = cryptoUrlPrefix + this.getClass().getResource("/" + cryptoUrl).toURI().getPath();
        }
        properties.put("ws-security.signature.properties", cryptoUrl);
        bean.setProperties(properties);
        XmlSigOutInterceptor sigInterceptor = new XmlSigOutInterceptor();
        if (enveloping) {
            sigInterceptor.setStyle(XmlSigOutInterceptor.ENVELOPING_SIG);
        }
        bean.getOutInterceptors().add(sigInterceptor);
        bean.setServiceClass(BookStore.class);
        
        BookStore store = bean.create(BookStore.class);
        try {
            Book book = store.addBook(new Book("CXF", 126L));
            assertEquals(126L, book.getId());
        } catch (WebApplicationException ex) {
            fail(ex.getMessage());
        } catch (ProcessingException ex) {
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                fail(ex.getCause().getMessage());
            } else {
                fail(ex.getMessage());
            }
        }
    }
    
    @Test
    public void testPostBookWithEnvelopedSig() throws Exception {
        String address = "https://localhost:" + test.port + "/xmlsig/bookstore/books";
        doTestSignature(address, false, false, true);
    }
    
    @Test
    public void testPostBookWithEnvelopedSigNoKeyInfo() throws Exception {
        String address = "https://localhost:" + test.port + "/xmlsignokeyinfo/bookstore/books";
        doTestSignature(address, false, false, false);
    }
    
    @Test
    public void testPostBookWithEnvelopingSig() throws Exception {
        if (STAX_PORT.equals(test.port)) {
            // TODO Supporting Enveloping
            return;
        }
        String address = "https://localhost:" + test.port + "/xmlsig/bookstore/books";
        doTestSignature(address, true, false, true);
    }
    
    @Test
    public void testPostBookWithEnvelopingSigFromResponse() throws Exception {
        if (STAX_PORT.equals(test.port)) {
            // TODO Supporting Enveloping
            return;
        }
        String address = "https://localhost:" + test.port + "/xmlsig/bookstore/books";
        doTestSignature(address, true, true, true);
    }
    
    private void doTestSignature(String address, 
                                 boolean enveloping, 
                                 boolean fromResponse,
                                 boolean useKeyInfo) {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress(address);
        
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JAXRSXmlSecTest.class.getResource("client.xml");
        Bus springBus = bf.createBus(busFile.toString());
        bean.setBus(springBus);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.signature.username", "alice");
        properties.put("ws-security.signature.properties", 
                       "org/apache/cxf/systest/jaxrs/security/alice.properties");
        bean.setProperties(properties);
        XmlSigOutInterceptor sigOutInterceptor = new XmlSigOutInterceptor();
        if (enveloping) {
            sigOutInterceptor.setStyle(XmlSigOutInterceptor.ENVELOPING_SIG);
        }
        sigOutInterceptor.setKeyInfoMustBeAvailable(useKeyInfo);
        bean.getOutInterceptors().add(sigOutInterceptor);
        XmlSigInInterceptor sigInInterceptor = new XmlSigInInterceptor();
        sigInInterceptor.setKeyInfoMustBeAvailable(useKeyInfo);
        bean.getInInterceptors().add(sigInInterceptor);
        
        WebClient wc = bean.createWebClient();
        WebClient.getConfig(wc).getHttpConduit().getClient().setReceiveTimeout(10000000L);
        try {
            Book book;
            if (!fromResponse) {
                book = wc.post(new Book("CXF", 126L), Book.class);
            } else {
                book = wc.post(new Book("CXF", 126L)).readEntity(Book.class);
            }
            assertEquals(126L, book.getId());
        } catch (WebApplicationException ex) {
            fail(ex.getMessage());
        } catch (ProcessingException ex) {
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                fail(ex.getCause().getMessage());
            } else {
                fail(ex.getMessage());
            }
        }
    }
    
    @Test
    public void testPostEncryptedBook() throws Exception {
        String address = "https://localhost:" + test.port + "/xmlenc/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        doTestPostEncryptedBook(address, false, properties);
    }
    
    @Test
    public void testPostEncryptedBookGCM() throws Exception {
        //
        // This test fails with the IBM JDK 7
        // IBM JDK 7 appears to require a GCMParameter class to be used, which
        // only exists in JDK 7. The Sun JDK appears to be more lenient and 
        // allows us to use the existing IVParameterSpec class.
        //
        if ("IBM Corporation".equals(System.getProperty("java.vendor"))
            && System.getProperty("java.version") != null
            &&  System.getProperty("java.version").startsWith("1.7")) {
            return;
        }
        
        String address = "https://localhost:" + test.port + "/xmlenc/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        String aes128GCM = "http://www.w3.org/2009/xmlenc11#aes128-gcm";
        doTestPostEncryptedBook(address, false, properties, SecurityUtils.X509_CERT, aes128GCM, null, false);
    }
    
    @Test
    public void testPostEncryptedBookSHA256() throws Exception {
        String address = "https://localhost:" + test.port + "/xmlenc/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        doTestPostEncryptedBook(
            address, false, properties, SecurityUtils.X509_CERT, XMLCipher.AES_128, XMLCipher.SHA256, false
        );
    }
    
    @Test
    public void testPostEncryptedBookIssuerSerial() throws Exception {
        String address = "https://localhost:" + test.port + "/xmlenc/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        doTestPostEncryptedBook(
            address, false, properties, SecurityUtils.X509_ISSUER_SERIAL, XMLCipher.AES_128, null, false
        );
    }
    
    @Test
    public void testPostEncryptedSignedBook() throws Exception {
        String address = "https://localhost:" + test.port + "/xmlsec-validate/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        properties.put("ws-security.signature.username", "alice");
        properties.put("ws-security.signature.properties", 
                       "org/apache/cxf/systest/jaxrs/security/alice.properties");
        doTestPostEncryptedBook(address, true, properties);
        
    }
    
    @Test
    public void testPostEncryptedSignedBookInvalid() throws Exception {
        String address = "https://localhost:" + test.port + "/xmlsec-validate/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        properties.put("ws-security.signature.username", "alice");
        properties.put("ws-security.signature.properties", 
                       "org/apache/cxf/systest/jaxrs/security/alice.properties");
        try {
            doTestPostEncryptedBook(address, true, properties, SecurityUtils.X509_CERT, 
                                "http://www.w3.org/2009/xmlenc11#aes128-gcm", null, true);
        } catch (BadRequestException ex) {
            assertEquals(400, ex.getResponse().getStatus());
        }
        
    }
    
    @Test
    public void testPostEncryptedSignedBookUseReqSigCert() throws Exception {
        if (STAX_PORT.equals(test.port)) {
            // TODO Supporting UseReqSigCert
            return;
        }
        String address = "https://localhost:" + test.port + "/xmlsec-useReqSigCert/bookstore/books";
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("ws-security.callback-handler", 
                       "org.apache.cxf.systest.jaxrs.security.saml.KeystorePasswordCallback");
        properties.put("ws-security.encryption.username", "bob");
        properties.put("ws-security.encryption.properties", 
                       "org/apache/cxf/systest/jaxrs/security/bob.properties");
        properties.put("ws-security.signature.username", "alice");
        properties.put("ws-security.signature.properties", 
                       "org/apache/cxf/systest/jaxrs/security/alice.properties");
        doTestPostEncryptedBook(address, true, properties);
    }
    
    public void doTestPostEncryptedBook(String address, boolean sign, Map<String, Object> properties) 
        throws Exception {
        doTestPostEncryptedBook(
            address, sign, properties, SecurityUtils.X509_CERT, XMLCipher.AES_128, null, false
        );
    }
    
    public void doTestPostEncryptedBook(
        String address, boolean sign, Map<String, Object> properties,
        String keyIdentifierType, String symmetricAlgorithm,
        String digestAlgorithm,
        boolean propagateException
    ) throws Exception {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setAddress(address);
        
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = JAXRSXmlSecTest.class.getResource("client.xml");
        Bus springBus = bf.createBus(busFile.toString());
        bean.setBus(springBus);

        bean.setProperties(properties);
        if (sign) {
            bean.getOutInterceptors().add(new XmlSigOutInterceptor());
        }
        XmlEncOutInterceptor encInterceptor = new XmlEncOutInterceptor();
        encInterceptor.setKeyIdentifierType(keyIdentifierType);
        encInterceptor.setSymmetricEncAlgorithm(symmetricAlgorithm);
        encInterceptor.setDigestAlgorithm(digestAlgorithm);
        bean.getOutInterceptors().add(encInterceptor);
        
        bean.getInInterceptors().add(new XmlEncInInterceptor());
        if (sign) {
            bean.getInInterceptors().add(new XmlSigInInterceptor());
        }
        
        
        WebClient wc = bean.createWebClient();
        WebClient.getConfig(wc).getHttpConduit().getClient().setReceiveTimeout(10000000L);
        try {
            Book book = wc.post(new Book("CXF", 126L), Book.class);
            assertEquals(126L, book.getId());
        } catch (WebApplicationException ex) {
            if (propagateException) {
                throw ex;
            } else {
                fail(ex.getMessage());
            }
        } catch (ProcessingException ex) {
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                fail(ex.getCause().getMessage());
            } else {
                fail(ex.getMessage());
            }
        }
        
    }
    
    private static final class TestParam {
        final String port;
        final boolean streaming;
        
        public TestParam(String p, boolean b) {
            port = p;
            streaming = b;
        }
        
        public String toString() {
            return port + ":" + (streaming ? "streaming" : "dom");
        }
        
    }
    
}
