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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.message.Message;
import org.apache.cxf.rs.security.saml.sso.SSOConstants;

public class SamlRedirectBindingFilter extends AbstractServiceProviderFilter {
    
    public Response handleRequest(Message m, ClassResourceInfo resourceClass) {
        if (checkSecurityContext(m)) {
            return null;
        } else {
            try {
                SamlRequestInfo info = createSamlRequestInfo(m);
                UriBuilder ub = UriBuilder.fromUri(getIdpServiceAddress());
                ub.queryParam(SSOConstants.SAML_REQUEST, info.getEncodedSamlRequest());
                ub.queryParam(SSOConstants.RELAY_STATE, info.getRelayState());    
                
                String contextCookie = createCookie(SSOConstants.RELAY_STATE,
                                                    info.getRelayState(),
                                                    info.getWebAppContext(),
                                                    info.getWebAppDomain());
                
                return Response.seeOther(ub.build())
                               .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
                               .header("Pragma", "no-cache") 
                               .header("Set-Cookie", contextCookie)
                               .build();
            } catch (Exception ex) {
                throw new WebApplicationException(ex);
            }
        }
    }
    
    
}
