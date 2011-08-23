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

package org.apache.cxf.rs.security.xml;

import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import javax.ws.rs.core.Response;
import javax.xml.stream.XMLStreamReader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.jaxrs.ext.RequestHandler;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.message.Message;
import org.apache.cxf.rs.security.common.CryptoLoader;
import org.apache.cxf.rs.security.common.TrustValidator;
import org.apache.cxf.staxutils.W3CDOMStreamReader;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.signature.Reference;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transform;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;

public class XmlSigInHandler extends AbstractXmlSecInHandler implements RequestHandler {
    
    private boolean removeSignature = true;
    
    public void setRemoveSignature(boolean remove) {
        this.removeSignature = remove;
    }
    
    public Response handleRequest(Message message, ClassResourceInfo resourceClass) {
        
        Document doc = getDocument(message);
        if (doc == null) {
            return null;
        }

        Element root = doc.getDocumentElement();
        Element sigElement = getSignatureElement(root);
        if (sigElement == null) {
            throwFault("Enveloped Signature is not available", null);
        }
        
        Crypto crypto = null;
        try {
            CryptoLoader loader = new CryptoLoader();
            crypto = loader.getCrypto(message, 
                               SecurityConstants.SIGNATURE_CRYPTO,
                               SecurityConstants.SIGNATURE_PROPERTIES);
            if (crypto == null) {
                crypto = loader.getCrypto(message, 
                                   SecurityConstants.ENCRYPT_CRYPTO,
                                   SecurityConstants.ENCRYPT_PROPERTIES);
            }
        } catch (Exception ex) {
            throwFault("Crypto can not be loaded", ex);
        }
        boolean valid = false;
        try {
            XMLSignature signature = new XMLSignature(sigElement, "");
            // See also WSS4J SAMLUtil.getCredentialFromKeyInfo 
            KeyInfo keyInfo = signature.getKeyInfo();
            
            X509Certificate cert = keyInfo.getX509Certificate();
            if (cert != null) {
                valid = signature.checkSignatureValue(cert);
            } else {
                PublicKey pk = keyInfo.getPublicKey();
                if (pk != null) {
                    valid = signature.checkSignatureValue(pk);
                }
            }
            // is this call redundant given that signature.checkSignatureValue uses References ?
            validateReference(root, signature);
            
            // validate trust 
            new TrustValidator().validateTrust(crypto, cert, keyInfo.getPublicKey());
            
        } catch (Exception ex) {
            throwFault("Signature validation failed", ex);
        }
        if (!valid) {
            throwFault("Signature validation failed", null);
        }
        if (removeSignature) {
            if (!isEnveloping(root)) {
                root.removeAttribute("ID");
                root.removeChild(sigElement);
            } else {
                Element actualBody = getActualBody(root);
                Document newDoc = DOMUtils.createDocument();
                newDoc.adoptNode(actualBody);
                root = actualBody;
            }
        }
        message.setContent(XMLStreamReader.class, 
                           new W3CDOMStreamReader(root));
        message.setContent(InputStream.class, null);
        
        //TODO: If we have a SAML assertion header as well with holder-of-key or
        // sender-vouches claims then we will need to store signature or parts of it
        // to validate that saml assertion and this payload have been signed by the 
        // same key
        
        return null;
    }
    
    private Element getActualBody(Element envelopingSigElement) {
        Element objectNode = getNode(envelopingSigElement, Constants.SignatureSpecNS, "Object", 0);
        if (objectNode == null) {
            throwFault("Object envelope is not available", null);
        }
        NodeList list = objectNode.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node instanceof Element) {
                objectNode.removeChild(node);
                return (Element)node;
            }
        }
        throwFault("No signed data is found", null);
        return null;
       
    }
    
    private Element getSignatureElement(Element root) {
        if (isEnveloping(root)) {    
            return root;
        }
        return getNode(root, Constants.SignatureSpecNS, "Signature", 0);
    }
    
    protected boolean isEnveloping(Element root) {
        return Constants.SignatureSpecNS.equals(root.getNamespaceURI())
                && "Signature".equals(root.getLocalName());
    }
    
    
    protected void validateReference(Element root, XMLSignature sig) {
        Reference ref = null;
        int count = sig.getSignedInfo().getLength();
        if (count != 1) {
            throwFault("Multiple Signature Reference are not currently supported", null);
        }
        try {
            ref = sig.getSignedInfo().item(0);
        } catch (XMLSecurityException ex) {
            throwFault("Signature Reference is not available", ex);
        }
        if (!isEnveloping(root)) {
            String rootId = root.getAttribute("ID");
            String refId = ref.getURI();
            if (refId.length() == 0 && rootId.length() == 0) {
                // or fragment must be expected ?
                return;
            }
            if (!refId.startsWith("#") || refId.length() <= 1 || !refId.substring(1).equals(rootId)) {
                throwFault("Signature Reference ID is invalid", null);
            }
        }
        Transforms transforms = null;
        try {
            transforms = ref.getTransforms();
        } catch (XMLSecurityException ex) {
            throwFault("Signature transforms can not be obtained", ex);
        }
        if (!isEnveloping(root)) {
            boolean isEnveloped = false;
            for (int i = 0; i < transforms.getLength(); i++) {
                try {
                    Transform tr = transforms.item(i);
                    if (Transforms.TRANSFORM_ENVELOPED_SIGNATURE.equals(tr.getURI())) {
                        isEnveloped = true;
                        break;
                    }
                } catch (Exception ex) {
                    throwFault("Problem accessing Transform instance", ex);    
                }
            }
            if (!isEnveloped) {
                throwFault("Only enveloped signatures are currently supported", null);
            }
        }
    }
}