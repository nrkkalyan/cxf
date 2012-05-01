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

package org.apache.cxf.rs.security.saml.sso.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.common.SAMLObjectBuilder;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml2.core.AuthnContextComparisonTypeEnumeration;
import org.opensaml.saml2.core.AuthnContextDeclRef;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameIDPolicy;
import org.opensaml.saml2.core.RequestedAuthnContext;
import org.opensaml.xml.XMLObjectBuilderFactory;

/**
* A set of utility methods to construct SAMLP Request statements
*/
public final class SamlpRequestComponentBuilder {
    
    private static SAMLObjectBuilder<AuthnRequest> authnRequestBuilder;
    
    private static SAMLObjectBuilder<Issuer> issuerBuilder;
    
    private static SAMLObjectBuilder<NameIDPolicy> nameIDBuilder;
    
    private static SAMLObjectBuilder<RequestedAuthnContext> requestedAuthnCtxBuilder;
    
    private static SAMLObjectBuilder<AuthnContextClassRef> requestedAuthnCtxClassRefBuilder;
    
    private static XMLObjectBuilderFactory builderFactory = Configuration.getBuilderFactory();
    
    private SamlpRequestComponentBuilder() {
        
    }
    
    @SuppressWarnings("unchecked")
    //CHECKSTYLE:OFF
    public static AuthnRequest createAuthnRequest(
        String serviceURL,
        boolean forceAuthn,
        boolean isPassive,
        String protocolBinding,
        SAMLVersion version,
        Issuer issuer,
        NameIDPolicy nameIDPolicy,
        RequestedAuthnContext requestedAuthnCtx
    ) {
    //CHECKSTYLE:ON    
        if (authnRequestBuilder == null) {
            authnRequestBuilder = (SAMLObjectBuilder<AuthnRequest>)
                builderFactory.getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME);
        }
        AuthnRequest authnRequest = authnRequestBuilder.buildObject();
        authnRequest.setAssertionConsumerServiceURL(serviceURL);
        authnRequest.setForceAuthn(forceAuthn);
        authnRequest.setID(UUID.randomUUID().toString());
        authnRequest.setIsPassive(isPassive);
        authnRequest.setIssueInstant(new DateTime());
        authnRequest.setProtocolBinding(protocolBinding);
        authnRequest.setVersion(version);
        
        authnRequest.setIssuer(issuer);
        authnRequest.setNameIDPolicy(nameIDPolicy);
        authnRequest.setRequestedAuthnContext(requestedAuthnCtx);
        
        return authnRequest;
    }
    
    @SuppressWarnings("unchecked")
    public static Issuer createIssuer(
        String issuerValue
    ) {
        if (issuerBuilder == null) {
            issuerBuilder = (SAMLObjectBuilder<Issuer>)
                builderFactory.getBuilder(Issuer.DEFAULT_ELEMENT_NAME);
        }
        Issuer issuer = issuerBuilder.buildObject();
        issuer.setValue(issuerValue);
        
        return issuer;
    }
    
    @SuppressWarnings("unchecked")
    public static NameIDPolicy createNameIDPolicy(
        boolean allowCreate,
        String format,
        String spNameQualifier
    ) {
        if (nameIDBuilder == null) {
            nameIDBuilder = (SAMLObjectBuilder<NameIDPolicy>)
                builderFactory.getBuilder(NameIDPolicy.DEFAULT_ELEMENT_NAME);
        }
        NameIDPolicy nameId = nameIDBuilder.buildObject();
        nameId.setAllowCreate(allowCreate);
        nameId.setFormat(format);
        nameId.setSPNameQualifier(spNameQualifier);
        
        return nameId;
    }
    
    @SuppressWarnings("unchecked")
    public static RequestedAuthnContext createRequestedAuthnCtxPolicy(
        AuthnContextComparisonTypeEnumeration comparison,
        List<AuthnContextClassRef> authnCtxClassRefList,
        List<AuthnContextDeclRef> authnCtxDeclRefList
    ) {
        if (requestedAuthnCtxBuilder == null) {
            requestedAuthnCtxBuilder = (SAMLObjectBuilder<RequestedAuthnContext>)
                builderFactory.getBuilder(RequestedAuthnContext.DEFAULT_ELEMENT_NAME);
        }
        RequestedAuthnContext authnCtx = requestedAuthnCtxBuilder.buildObject();
        authnCtx.setComparison(comparison);
        
        if (authnCtxClassRefList != null) {
            List<AuthnContextClassRef> classRefList = authnCtx.getAuthnContextClassRefs();
            if (classRefList == null) {
                classRefList = new ArrayList<AuthnContextClassRef>();
            }
            classRefList.addAll(authnCtxClassRefList);
        }
        
        if (authnCtxDeclRefList != null) {
            List<AuthnContextDeclRef> declRefList = authnCtx.getAuthnContextDeclRefs();
            if (declRefList == null) {
                declRefList = new ArrayList<AuthnContextDeclRef>();
            }
            declRefList.addAll(authnCtxDeclRefList);
        }
        
        return authnCtx;
    }
    
    @SuppressWarnings("unchecked")
    public static AuthnContextClassRef createAuthnCtxClassRef(
        String authnCtxClassRefValue
    ) {
        if (requestedAuthnCtxClassRefBuilder == null) {
            requestedAuthnCtxClassRefBuilder = (SAMLObjectBuilder<AuthnContextClassRef>)
                builderFactory.getBuilder(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
        }
        AuthnContextClassRef authnCtxClassRef = requestedAuthnCtxClassRefBuilder.buildObject();
        authnCtxClassRef.setAuthnContextClassRef(authnCtxClassRefValue);
        
        return authnCtxClassRef;
    }
    
}