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
package org.apache.cxf.sts.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.security.auth.callback.CallbackHandler;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.apache.cxf.jaxws.context.WebServiceContextImpl;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.sts.QNameConstants;
import org.apache.cxf.sts.STSConstants;
import org.apache.cxf.sts.STSPropertiesMBean;
import org.apache.cxf.sts.StaticSTSProperties;
import org.apache.cxf.sts.common.PasswordCallbackHandler;
import org.apache.cxf.sts.request.KeyRequirements;
import org.apache.cxf.sts.request.TokenRequirements;
import org.apache.cxf.sts.service.EncryptionProperties;
import org.apache.cxf.sts.service.ServiceMBean;
import org.apache.cxf.sts.service.StaticService;
import org.apache.cxf.sts.token.provider.SAMLTokenProvider;
import org.apache.cxf.sts.token.provider.TokenProvider;
import org.apache.cxf.sts.token.provider.TokenProviderParameters;
import org.apache.cxf.sts.token.provider.TokenProviderResponse;
import org.apache.cxf.sts.token.realm.SAMLRealm;
import org.apache.cxf.sts.token.validator.IssuerSAMLRealmCodec;
import org.apache.cxf.sts.token.validator.SAMLTokenValidator;
import org.apache.cxf.sts.token.validator.TokenValidator;
import org.apache.cxf.sts.token.validator.UsernameTokenValidator;
import org.apache.cxf.ws.security.sts.provider.STSException;
import org.apache.cxf.ws.security.sts.provider.model.OnBehalfOfType;
import org.apache.cxf.ws.security.sts.provider.model.RequestSecurityTokenResponseCollectionType;
import org.apache.cxf.ws.security.sts.provider.model.RequestSecurityTokenResponseType;
import org.apache.cxf.ws.security.sts.provider.model.RequestSecurityTokenType;
import org.apache.cxf.ws.security.sts.provider.model.RequestedSecurityTokenType;
import org.apache.cxf.ws.security.sts.provider.model.secext.AttributedString;
import org.apache.cxf.ws.security.sts.provider.model.secext.PasswordString;
import org.apache.cxf.ws.security.sts.provider.model.secext.UsernameTokenType;
import org.apache.ws.security.CustomTokenPrincipal;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoFactory;
import org.apache.ws.security.saml.ext.builder.SAML2Constants;
import org.apache.ws.security.util.DOM2Writer;


/**
 * Some unit tests for the issue operation.
 */
public class IssueOnbehalfofUnitTest extends org.junit.Assert {

    public static final QName REQUESTED_SECURITY_TOKEN = 
        QNameConstants.WS_TRUST_FACTORY.createRequestedSecurityToken(null).getName();
    
    /**
     * Test to successfully issue a SAML 2 token on-behalf-of a SAML 2 token
     */
    @org.junit.Test
    public void testIssueSaml2TokenOnBehalfOfSaml2() throws Exception {
        TokenIssueOperation issueOperation = new TokenIssueOperation();

        // Add Token Provider
        List<TokenProvider> providerList = new ArrayList<TokenProvider>();
        providerList.add(new SAMLTokenProvider());
        issueOperation.setTokenProviders(providerList);
        
        // Add Token Validator
        List<TokenValidator> validatorList = new ArrayList<TokenValidator>();
        validatorList.add(new SAMLTokenValidator());
        issueOperation.setTokenValidators(validatorList);

        // Add Service
        ServiceMBean service = new StaticService();
        service.setEndpoints(Collections.singletonList("http://dummy-service.com/dummy"));
        issueOperation.setServices(Collections.singletonList(service));

        // Add STSProperties object
        STSPropertiesMBean stsProperties = new StaticSTSProperties();
        Crypto crypto = CryptoFactory.getInstance(getEncryptionProperties());
        stsProperties.setEncryptionCrypto(crypto);
        stsProperties.setSignatureCrypto(crypto);
        stsProperties.setEncryptionUsername("myservicekey");
        stsProperties.setSignatureUsername("mystskey");
        stsProperties.setCallbackHandler(new PasswordCallbackHandler());
        stsProperties.setIssuer("STS");
        issueOperation.setStsProperties(stsProperties);

        // Mock up a request
        RequestSecurityTokenType request = new RequestSecurityTokenType();
        JAXBElement<String> tokenType = 
            new JAXBElement<String>(
                    QNameConstants.TOKEN_TYPE, String.class, WSConstants.WSS_SAML2_TOKEN_TYPE
            );
        request.getAny().add(tokenType);

        // Get a SAML Token via the SAMLTokenProvider
        CallbackHandler callbackHandler = new PasswordCallbackHandler();
        Element samlToken = 
            createSAMLAssertion(WSConstants.WSS_SAML2_TOKEN_TYPE, crypto, "mystskey", callbackHandler);
        Document doc = samlToken.getOwnerDocument();
        samlToken = (Element)doc.appendChild(samlToken);
        OnBehalfOfType onbehalfof = new OnBehalfOfType();
        onbehalfof.setAny(samlToken);

        JAXBElement<OnBehalfOfType> onbehalfofType = 
            new JAXBElement<OnBehalfOfType>(
                    QNameConstants.ON_BEHALF_OF, OnBehalfOfType.class, onbehalfof
            );
        request.getAny().add(onbehalfofType);

        // Mock up message context
        MessageImpl msg = new MessageImpl();
        WrappedMessageContext msgCtx = new WrappedMessageContext(msg);
        WebServiceContextImpl webServiceContext = new WebServiceContextImpl(msgCtx);

        // Issue a token
        RequestSecurityTokenResponseCollectionType response = 
            issueOperation.issue(request, webServiceContext);
        List<RequestSecurityTokenResponseType> securityTokenResponse = 
            response.getRequestSecurityTokenResponse();
        assertTrue(!securityTokenResponse.isEmpty());
    }
    
    
    /**
     * Test to successfully issue a SAML 2 token on-behalf-of a UsernameToken
     */
    @org.junit.Test
    public void testIssueSaml2TokenOnBehalfOfUsernameToken() throws Exception {
        TokenIssueOperation issueOperation = new TokenIssueOperation();

        // Add Token Provider
        List<TokenProvider> providerList = new ArrayList<TokenProvider>();
        providerList.add(new SAMLTokenProvider());
        issueOperation.setTokenProviders(providerList);
        
        // Add Token Validator
        List<TokenValidator> validatorList = new ArrayList<TokenValidator>();
        validatorList.add(new UsernameTokenValidator());
        issueOperation.setTokenValidators(validatorList);

        // Add Service
        ServiceMBean service = new StaticService();
        service.setEndpoints(Collections.singletonList("http://dummy-service.com/dummy"));
        issueOperation.setServices(Collections.singletonList(service));

        // Add STSProperties object
        STSPropertiesMBean stsProperties = new StaticSTSProperties();
        Crypto crypto = CryptoFactory.getInstance(getEncryptionProperties());
        stsProperties.setEncryptionCrypto(crypto);
        stsProperties.setSignatureCrypto(crypto);
        stsProperties.setEncryptionUsername("myservicekey");
        stsProperties.setSignatureUsername("mystskey");
        stsProperties.setCallbackHandler(new PasswordCallbackHandler());
        stsProperties.setIssuer("STS");
        issueOperation.setStsProperties(stsProperties);

        // Mock up a request
        RequestSecurityTokenType request = new RequestSecurityTokenType();
        JAXBElement<String> tokenType = 
            new JAXBElement<String>(
                    QNameConstants.TOKEN_TYPE, String.class, WSConstants.WSS_SAML2_TOKEN_TYPE
            );
        request.getAny().add(tokenType);

        
        // Create a UsernameToken
        JAXBElement<UsernameTokenType> usernameTokenType = createUsernameToken("alice", "clarinet");
        OnBehalfOfType onbehalfof = new OnBehalfOfType();
        onbehalfof.setAny(usernameTokenType);
        
        JAXBElement<OnBehalfOfType> onbehalfofType = 
            new JAXBElement<OnBehalfOfType>(
                    QNameConstants.ON_BEHALF_OF, OnBehalfOfType.class, onbehalfof
            );
        request.getAny().add(onbehalfofType);
        

        // Mock up message context
        MessageImpl msg = new MessageImpl();
        WrappedMessageContext msgCtx = new WrappedMessageContext(msg);
        WebServiceContextImpl webServiceContext = new WebServiceContextImpl(msgCtx);

        // Issue a token
        RequestSecurityTokenResponseCollectionType response = 
            issueOperation.issue(request, webServiceContext);
        List<RequestSecurityTokenResponseType> securityTokenResponse = 
            response.getRequestSecurityTokenResponse();
        assertTrue(!securityTokenResponse.isEmpty());
    }
    
    /**
     * Test to successfully issue a SAML 2 token on-behalf-of a UsernameToken
     */
    @org.junit.Test
    public void testIssueSaml2TokenOnBehalfOfInvalidUsernameToken() throws Exception {
        TokenIssueOperation issueOperation = new TokenIssueOperation();

        // Add Token Provider
        List<TokenProvider> providerList = new ArrayList<TokenProvider>();
        providerList.add(new SAMLTokenProvider());
        issueOperation.setTokenProviders(providerList);
        
        // Add Token Validator
        List<TokenValidator> validatorList = new ArrayList<TokenValidator>();
        validatorList.add(new UsernameTokenValidator());
        issueOperation.setTokenValidators(validatorList);

        // Add Service
        ServiceMBean service = new StaticService();
        service.setEndpoints(Collections.singletonList("http://dummy-service.com/dummy"));
        issueOperation.setServices(Collections.singletonList(service));

        // Add STSProperties object
        STSPropertiesMBean stsProperties = new StaticSTSProperties();
        Crypto crypto = CryptoFactory.getInstance(getEncryptionProperties());
        stsProperties.setEncryptionCrypto(crypto);
        stsProperties.setSignatureCrypto(crypto);
        stsProperties.setEncryptionUsername("myservicekey");
        stsProperties.setSignatureUsername("mystskey");
        stsProperties.setCallbackHandler(new PasswordCallbackHandler());
        stsProperties.setIssuer("STS");
        issueOperation.setStsProperties(stsProperties);

        // Mock up a request
        RequestSecurityTokenType request = new RequestSecurityTokenType();
        JAXBElement<String> tokenType = 
            new JAXBElement<String>(
                    QNameConstants.TOKEN_TYPE, String.class, WSConstants.WSS_SAML2_TOKEN_TYPE
            );
        request.getAny().add(tokenType);

        
        // Create a UsernameToken without password
        JAXBElement<UsernameTokenType> usernameTokenType = createUsernameToken("alice", null);
        OnBehalfOfType onbehalfof = new OnBehalfOfType();
        onbehalfof.setAny(usernameTokenType);
        
        JAXBElement<OnBehalfOfType> onbehalfofType = 
            new JAXBElement<OnBehalfOfType>(
                    QNameConstants.ON_BEHALF_OF, OnBehalfOfType.class, onbehalfof
            );
        request.getAny().add(onbehalfofType);
        

        // Mock up message context
        MessageImpl msg = new MessageImpl();
        WrappedMessageContext msgCtx = new WrappedMessageContext(msg);
        WebServiceContextImpl webServiceContext = new WebServiceContextImpl(msgCtx);

        // Issue a token - this will fail as the UsernameToken validation fails
        try {
            issueOperation.issue(request, webServiceContext);
            fail("Failure expected as no principal is available to create SAML assertion");
        } catch (STSException ex) {
            // expected
        }
    }
    

    /**
     * Test to successfully issue a SAML 2 token (realm "B") on-behalf-of a SAML 2 token
     * on-behalf-of token is issued by realm "A".
     */
    @org.junit.Test
    public void testIssueSaml2TokenOnBehalfOfSaml2DifferentRealm() throws Exception {
        TokenIssueOperation issueOperation = new TokenIssueOperation();
        
        // Add Token Provider
        List<TokenProvider> providerList = new ArrayList<TokenProvider>();
        SAMLTokenProvider samlTokenProvider = new SAMLTokenProvider();
        providerList.add(samlTokenProvider);
        issueOperation.setTokenProviders(providerList);
        
        // Add Token Validator
        List<TokenValidator> validatorList = new ArrayList<TokenValidator>();
        SAMLTokenValidator samlTokenValidator = new SAMLTokenValidator();
        samlTokenValidator.setSamlRealmCodec(new IssuerSAMLRealmCodec());
        validatorList.add(samlTokenValidator);
        issueOperation.setTokenValidators(validatorList);

        // Add Service
        ServiceMBean service = new StaticService();
        service.setEndpoints(Collections.singletonList("http://dummy-service.com/dummy"));
        issueOperation.setServices(Collections.singletonList(service));
        
        // Add STSProperties object
        STSPropertiesMBean stsProperties = new StaticSTSProperties();
        Crypto crypto = CryptoFactory.getInstance(getEncryptionProperties());
        stsProperties.setEncryptionCrypto(crypto);
        stsProperties.setSignatureCrypto(crypto);
        stsProperties.setEncryptionUsername("myservicekey");
        stsProperties.setSignatureUsername("mystskey");
        stsProperties.setCallbackHandler(new PasswordCallbackHandler());
        stsProperties.setIssuer("STS");
        stsProperties.setRealmParser(new CustomRealmParser());
        stsProperties.setIdentityMapper(new CustomIdentityMapper());
        issueOperation.setStsProperties(stsProperties);
        
        Map<String, SAMLRealm> realms = createSamlRealms();
        
        // Mock up a request
        RequestSecurityTokenType request = new RequestSecurityTokenType();
        JAXBElement<String> tokenType = 
            new JAXBElement<String>(
                QNameConstants.TOKEN_TYPE, String.class, WSConstants.WSS_SAML2_TOKEN_TYPE
            );
        request.getAny().add(tokenType);
        
        // Get a SAML Token via the SAMLTokenProvider
        CallbackHandler callbackHandler = new PasswordCallbackHandler();
        Element samlToken = 
            createSAMLAssertion(WSConstants.WSS_SAML2_TOKEN_TYPE, crypto, "mystskey",
                    callbackHandler, realms);
        Document doc = samlToken.getOwnerDocument();
        samlToken = (Element)doc.appendChild(samlToken);
        OnBehalfOfType onbehalfof = new OnBehalfOfType();
        onbehalfof.setAny(samlToken);

        JAXBElement<OnBehalfOfType> onbehalfofType = 
            new JAXBElement<OnBehalfOfType>(
                    QNameConstants.ON_BEHALF_OF, OnBehalfOfType.class, onbehalfof
            );
        request.getAny().add(onbehalfofType);
        
        // Mock up message context
        MessageImpl msg = new MessageImpl();
        WrappedMessageContext msgCtx = new WrappedMessageContext(msg);
        msgCtx.put("url", "https");
        WebServiceContextImpl webServiceContext = new WebServiceContextImpl(msgCtx);
        
        // Validate a token - this will fail as the tokenProvider doesn't understand how to handle
        // realm "B"
        //try {
        //    issueOperation.issue(request, webServiceContext);
        //} catch (STSException ex) {
        //    // expected
        //}
        
        samlTokenProvider.setRealmMap(realms);
        
        RequestSecurityTokenResponseCollectionType response = 
            issueOperation.issue(request, webServiceContext);
        List<RequestSecurityTokenResponseType> securityTokenResponseList = 
            response.getRequestSecurityTokenResponse();

        assertTrue(!securityTokenResponseList.isEmpty());       
        RequestSecurityTokenResponseType securityTokenResponse = securityTokenResponseList.get(0);
        
        // Test the generated token.
        Element assertion = null;
        for (Object tokenObject : securityTokenResponse.getAny()) {
            if (tokenObject instanceof JAXBElement<?>
                && REQUESTED_SECURITY_TOKEN.equals(((JAXBElement<?>)tokenObject).getName())) {
                RequestedSecurityTokenType rstType = 
                    (RequestedSecurityTokenType)((JAXBElement<?>)tokenObject).getValue();
                assertion = (Element)rstType.getAny();
                break;
            }
        }
        
        assertNotNull(assertion);
        String tokenString = DOM2Writer.nodeToString(assertion);
        assertTrue(tokenString.contains("AttributeStatement"));
        assertTrue(tokenString.contains("ALICE"));
        assertTrue(tokenString.contains(SAML2Constants.CONF_BEARER));
    }    
    

    /*
     * Mock up an SAML assertion element
     */
    private Element createSAMLAssertion(
            String tokenType, Crypto crypto, String signatureUsername, CallbackHandler callbackHandler
    ) throws WSSecurityException {
        return createSAMLAssertion(tokenType, crypto, signatureUsername, callbackHandler, null);
    }
    
    /*
     * Mock up an SAML assertion element
     */
    private Element createSAMLAssertion(
            String tokenType, Crypto crypto, String signatureUsername, CallbackHandler callbackHandler,
            Map<String, SAMLRealm> realms
    ) throws WSSecurityException {
        SAMLTokenProvider samlTokenProvider = new SAMLTokenProvider();
        samlTokenProvider.setRealmMap(realms);
        
        TokenProviderParameters providerParameters = 
            createProviderParameters(
                    tokenType, STSConstants.BEARER_KEY_KEYTYPE, crypto, signatureUsername, callbackHandler
            );
        if (realms != null) {
            providerParameters.setRealm("A");
        }
        TokenProviderResponse providerResponse = samlTokenProvider.createToken(providerParameters);
        assertTrue(providerResponse != null);
        assertTrue(providerResponse.getToken() != null && providerResponse.getTokenId() != null);

        return providerResponse.getToken();
    }

    private TokenProviderParameters createProviderParameters(
            String tokenType, String keyType, Crypto crypto, 
            String signatureUsername, CallbackHandler callbackHandler
    ) throws WSSecurityException {
        TokenProviderParameters parameters = new TokenProviderParameters();

        TokenRequirements tokenRequirements = new TokenRequirements();
        tokenRequirements.setTokenType(tokenType);
        parameters.setTokenRequirements(tokenRequirements);

        KeyRequirements keyRequirements = new KeyRequirements();
        keyRequirements.setKeyType(keyType);
        parameters.setKeyRequirements(keyRequirements);

        parameters.setPrincipal(new CustomTokenPrincipal("alice"));
        // Mock up message context
        MessageImpl msg = new MessageImpl();
        WrappedMessageContext msgCtx = new WrappedMessageContext(msg);
        WebServiceContextImpl webServiceContext = new WebServiceContextImpl(msgCtx);
        parameters.setWebServiceContext(webServiceContext);

        parameters.setAppliesToAddress("http://dummy-service.com/dummy");

        // Add STSProperties object
        StaticSTSProperties stsProperties = new StaticSTSProperties();
        stsProperties.setSignatureCrypto(crypto);
        stsProperties.setSignatureUsername(signatureUsername);
        stsProperties.setCallbackHandler(callbackHandler);
        stsProperties.setIssuer("STS");
        parameters.setStsProperties(stsProperties);

        parameters.setEncryptionProperties(new EncryptionProperties());

        return parameters;
    }

    private JAXBElement<UsernameTokenType> createUsernameToken(String name, String password) {
        UsernameTokenType usernameToken = new UsernameTokenType();
        AttributedString username = new AttributedString();
        username.setValue(name);
        usernameToken.setUsername(username);
        
        // Add a password
        if (password != null) {
            PasswordString passwordString = new PasswordString();
            passwordString.setValue(password);
            passwordString.setType(WSConstants.PASSWORD_TEXT);
            JAXBElement<PasswordString> passwordType = 
                new JAXBElement<PasswordString>(
                        QNameConstants.PASSWORD, PasswordString.class, passwordString
                );
            usernameToken.getAny().add(passwordType);
        }
        
        JAXBElement<UsernameTokenType> tokenType = 
            new JAXBElement<UsernameTokenType>(
                QNameConstants.USERNAME_TOKEN, UsernameTokenType.class, usernameToken
            );
        
        return tokenType;
    }
    
    private Map<String, SAMLRealm> createSamlRealms() {
        // Create Realms
        Map<String, SAMLRealm> samlRealms = new HashMap<String, SAMLRealm>();
        SAMLRealm samlRealm = new SAMLRealm();
        samlRealm.setIssuer("A-Issuer");
        samlRealms.put("A", samlRealm);
        samlRealm = new SAMLRealm();
        samlRealm.setIssuer("B-Issuer");
        samlRealms.put("B", samlRealm);
        return samlRealms;
    }

    private Properties getEncryptionProperties() {
        Properties properties = new Properties();
        properties.put(
                "org.apache.ws.security.crypto.provider", "org.apache.ws.security.components.crypto.Merlin"
        );
        properties.put("org.apache.ws.security.crypto.merlin.keystore.password", "stsspass");
        properties.put("org.apache.ws.security.crypto.merlin.keystore.file", "stsstore.jks");

        return properties;
    }


}
