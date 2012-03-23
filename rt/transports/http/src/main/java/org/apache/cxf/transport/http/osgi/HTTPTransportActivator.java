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

package org.apache.cxf.transport.http.osgi;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.configuration.jsse.spring.TLSParameterJaxBUtils;
import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.configuration.security.CertStoreType;
import org.apache.cxf.configuration.security.CertificateConstraintsType;
import org.apache.cxf.configuration.security.CombinatorType;
import org.apache.cxf.configuration.security.DNConstraintsType;
import org.apache.cxf.configuration.security.FiltersType;
import org.apache.cxf.configuration.security.KeyManagersType;
import org.apache.cxf.configuration.security.KeyStoreType;
import org.apache.cxf.configuration.security.ProxyAuthorizationPolicy;
import org.apache.cxf.configuration.security.SecureRandomParameters;
import org.apache.cxf.configuration.security.TrustManagersType;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.HTTPConduitConfigurer;
import org.apache.cxf.transports.http.configuration.ConnectionType;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.transports.http.configuration.ProxyServerType;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.tracker.ServiceTracker;

/**
 * This class registers a HTTPConduitConfigurer that will pull information from the 
 * config:admin service to configure conduits.   With the Felix file based impl, the
 * format for that would be in files named org.apache.cxf.http.conduits-XYZ.cfg
 * that has a list of properties like:
 * 
 * url: Regex url to match the configuration
 * order: Integer order in which to apply the regex's when multiple regex's match.
 * client.*
 * tlsClientParameters.*
 * proxyAuthorization.*
 * authorization.*
 * 
 * Where each of those is a prefix for the attributes that would be on the elements 
 * of the http:conduit configuration defined at:
 * 
 * http://cxf.apache.org/schemas/configuration/http-conf.xsd
 * 
 * For example:
 * client.ReceiveTimeout: 1000
 * authorization.Username: Foo
 * tlsClientParameters.keyManagers.keyStore.file: mykeys.jks
 * etc....
 * 
 */
public class HTTPTransportActivator 
    implements BundleActivator, ManagedServiceFactory, HTTPConduitConfigurer {
    public static final String FACTORY_PID = "org.apache.cxf.http.conduits"; 
    private static final String SECURE_HTTP_PREFIX = "https";
    
    private static class PidInfo implements Comparable<PidInfo> {
        final Dictionary<String, String> props;
        final Matcher matcher;
        final int order;
        
        public PidInfo(Dictionary<String, String> p, Matcher m, int o) {
            matcher = m;
            props = p;
            order = o;
        }
        public Dictionary<String, String> getProps() {
            return props;
        }
        public Matcher getMatcher() {
            return matcher;
        }

        public int compareTo(PidInfo o) {
            if (order < o.order) {
                return -1;
            } else if (order > o.order) { 
                return 1;
            }
            // priorities are equal
            if (matcher != null) {
                if (o.matcher == null) {
                    return -1;
                }
                return matcher.pattern().toString().compareTo(o.matcher.pattern().toString());
            }
            return 0;
        }
    }
    
    ServiceTracker configAdminTracker;
    ServiceRegistration reg;
    ServiceRegistration reg2;
    Map<String, PidInfo> props 
        = new ConcurrentHashMap<String, PidInfo>();
    CopyOnWriteArrayList<PidInfo> sorted = new CopyOnWriteArrayList<PidInfo>();
    
    public void start(BundleContext context) throws Exception {
        Properties servProps = new Properties();
        servProps.put(Constants.SERVICE_PID, FACTORY_PID);  
        reg2 = context.registerService(ManagedServiceFactory.class.getName(),
                                       this, servProps);
        
        servProps = new Properties();
        servProps.put(Constants.SERVICE_PID,  "org.apache.cxf.http.conduit-configurer");  
        reg = context.registerService(HTTPConduitConfigurer.class.getName(),
                                this, servProps);
        
        configAdminTracker = new ServiceTracker(context, ConfigurationAdmin.class.getName(), null);
        configAdminTracker.open();
    }

    public void stop(BundleContext context) throws Exception {
        reg.unregister();
        reg2.unregister();
        configAdminTracker.close();
    }

    public String getName() {
        return FACTORY_PID;
    }

    @SuppressWarnings("unchecked")
    public void updated(String pid, @SuppressWarnings("rawtypes") Dictionary properties)
        throws ConfigurationException {
        if (pid == null) {
            return;
        }
        String url = (String)properties.get("url");
        String name = (String)properties.get("name");
        Matcher matcher = url == null ? null : Pattern.compile(url).matcher("");
        String p = (String)properties.get("order");
        int order = 50; 
        if (p != null) {
            order = Integer.valueOf(p);
        }
        
        PidInfo info = new PidInfo(properties, matcher, order);
        
        props.put(pid, info);
        if (url != null) {
            props.put(url, info);
        }
        if (name != null) {
            props.put(name, info);
        }
        addToSortedInfos(info);
    }

    private synchronized void addToSortedInfos(PidInfo pi) {
        int size = sorted.size();
        for (int x = 0; x < size; x++) {
            PidInfo p = sorted.get(x);
            if (pi.compareTo(p) < 0) {
                sorted.add(x, pi);
                return;
            }
        }
        sorted.add(pi);
    }
    private synchronized void removeFromSortedInfos(PidInfo pi) {
        sorted.remove(pi);
    }

    public void deleted(String pid) {
        PidInfo info = props.remove(pid);
        if (info == null) {
            return;
        }
        removeFromSortedInfos(info);
        Dictionary<String, String> d = info.getProps();
        if (d != null) {
            String url = d.get("url");
            String name = d.get("name");
            if (url != null) {
                props.remove(url);
            }
            if (name != null) {
                props.remove(name);
            }
        }
    }

    public void configure(String name, String address, HTTPConduit c) {
        PidInfo byName = null;
        PidInfo byAddress = null;
        if (name != null) {
            byName = props.get(name);
        }
        if (address != null) {
            byAddress = props.get(address);
            if (byAddress == byName) {
                byAddress = null;
            }
        }
        
        for (PidInfo info : sorted) {
            if (info.getMatcher() != null
                && info != byName
                && info != byAddress) {
                Matcher m = info.getMatcher();
                synchronized (m) {
                    m.reset(address);
                    if (m.matches()) {
                        apply(info.getProps(), c, address);
                    }
                }
            }
        }
        
        if (byAddress != null) {
            apply(byAddress.getProps(), c, address);
        }
        if (byName != null) {
            apply(byName.getProps(), c, address);
        }
    }

    private void apply(Dictionary<String, String> d, HTTPConduit c, String address) {
        applyClientPolicies(d, c);
        applyAuthorization(d, c);
        applyProxyAuthorization(d, c);
        if (address != null && address.startsWith(SECURE_HTTP_PREFIX)) {
            applyTlsClientParameters(d, c);
        }
    }

    private void applyTlsClientParameters(Dictionary<String, String> d, HTTPConduit c) {
        Enumeration<String> keys = d.keys();
        TLSClientParameters p = c.getTlsClientParameters();
        SecureRandomParameters srp = null;
        KeyManagersType kmt = null;
        TrustManagersType tmt = null;
        while (keys.hasMoreElements()) {
            String k = keys.nextElement();
            if (k.startsWith("tlsClientParameters.")) {
                if (p == null) {
                    p = new TLSClientParameters();
                    c.setTlsClientParameters(p);
                }
                String v = d.get(k);
                k = k.substring("tlsClientParameters.".length());

                if ("secureSocketProtocol".equals(k)) {
                    p.setSecureSocketProtocol(v);
                } else if ("sslCacheTimeout".equals(k)) {
                    p.setSslCacheTimeout(Integer.parseInt(v));
                } else if ("jsseProvider".equals(k)) {
                    p.setJsseProvider(v);
                } else if ("disableCNCheck".equals(k)) {
                    p.setDisableCNCheck(Boolean.parseBoolean(v));
                } else if ("useHttpsURLConnectionDefaultHostnameVerifier".equals(k)) {
                    p.setUseHttpsURLConnectionDefaultHostnameVerifier(Boolean.parseBoolean(v));
                } else if ("useHttpsURLConnectionDefaultSslSocketFactory".equals(k)) {
                    p.setUseHttpsURLConnectionDefaultSslSocketFactory(Boolean.parseBoolean(v));
                } else if (k.startsWith("certConstraints.")) {
                    k = k.substring("certConstraints.".length());
                    CertificateConstraintsType cct = p.getCertConstraints();
                    if (cct == null) {
                        cct = new CertificateConstraintsType();
                        p.setCertConstraints(cct);
                    }
                    DNConstraintsType dnct = null;
                    if (k.startsWith("SubjectDNConstraints.")) {
                        dnct = cct.getSubjectDNConstraints();
                        if (dnct == null) {
                            dnct = new DNConstraintsType();
                            cct.setSubjectDNConstraints(dnct);
                        }
                        k = k.substring("SubjectDNConstraints.".length());
                    } else if (k.startsWith("IssuerDNConstraints.")) {
                        dnct = cct.getIssuerDNConstraints();
                        if (dnct == null) {
                            dnct = new DNConstraintsType();
                            cct.setIssuerDNConstraints(dnct);
                        }
                        k = k.substring("IssuerDNConstraints.".length());
                    }
                    if (dnct != null) {
                        if ("combinator".equals(k)) {
                            dnct.setCombinator(CombinatorType.fromValue(v));
                        } else if ("RegularExpression".equals(k)) {
                            dnct.getRegularExpression().add(k);
                        }
                    }
                } else if (k.startsWith("secureRandomParameters.")) {
                    k = k.substring("secureRandomParameters.".length());
                    if (srp == null) {
                        srp = new SecureRandomParameters();
                    }
                    if ("algorithm".equals(k)) {
                        srp.setAlgorithm(v);
                    } else if ("provider".equals(k)) {
                        srp.setProvider(v);
                    }
                } else if (k.startsWith("cipherSuitesFilter.")) {
                    k = k.substring("cipherSuitesFilter.".length());
                    StringTokenizer st = new StringTokenizer(v, ",");
                    FiltersType ft = p.getCipherSuitesFilter();
                    if (ft == null) {
                        ft = new FiltersType();
                        p.setCipherSuitesFilter(ft);
                    }
                    List<String> lst = "include".equals(k) ? ft.getInclude() : ft.getExclude();
                    while (st.hasMoreTokens()) {
                        lst.add(st.nextToken());
                    }
                } else if (k.startsWith("cipherSuites")) {
                    StringTokenizer st = new StringTokenizer(v, ",");
                    while (st.hasMoreTokens()) {
                        p.getCipherSuites().add(st.nextToken());
                    }
                } else if (k.startsWith("trustManagers.")) {
                    tmt = getTrustManagers(tmt,
                                          k.substring("trustManagers.".length()),
                                          v);
                } else if (k.startsWith("keyManagers.")) {
                    kmt = getKeyManagers(kmt,
                                         k.substring("keyManagers.".length()),
                                         v);
                }
            }
        }
        
        try {
            if (srp != null) {
                p.setSecureRandom(TLSParameterJaxBUtils.getSecureRandom(srp));
            }
            if (kmt != null) {
                p.setKeyManagers(TLSParameterJaxBUtils.getKeyManagers(kmt));
            }
            if (tmt != null) {
                p.setTrustManagers(TLSParameterJaxBUtils.getTrustManagers(tmt));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private KeyManagersType getKeyManagers(KeyManagersType keyManagers, String k, String v) {
        if (keyManagers == null) {
            keyManagers = new KeyManagersType();
        }
        if ("factoryAlgorithm".equals(k)) {
            keyManagers.setFactoryAlgorithm(v);
        } else if ("provider".equals(k)) {
            keyManagers.setProvider(v);
        } else if ("keyPassword".equals(k)) {
            keyManagers.setKeyPassword(v);
        } else if (k.startsWith("keyStore.")) {
            keyManagers.setKeyStore(getKeyStore(keyManagers.getKeyStore(),
                                                k.substring("keyStore.".length()),
                                                v));
        }
        return keyManagers;
    }

    private KeyStoreType getKeyStore(KeyStoreType ks, String k, String v) {
        if (ks == null) {
            ks = new KeyStoreType();
        }
        if ("type".equals(k)) {
            ks.setType(v);
        } else if ("password".equals(k)) {
            ks.setPassword(v);
        } else if ("provider".equals(k)) {
            ks.setProvider(v);
        } else if ("url".equals(k)) {
            ks.setUrl(v);
        } else if ("file".equals(k)) {
            ks.setFile(v);
        } else if ("resource".equals(k)) {
            ks.setResource(v);
        }
        return ks;
    }

    private TrustManagersType getTrustManagers(TrustManagersType tmt, String k, String v) {
        if (tmt == null) {
            tmt = new TrustManagersType();
        }
        if ("provider".equals(k)) {
            tmt.setProvider(v);
        } else if ("factoryAlgorithm".equals(k)) {
            tmt.setFactoryAlgorithm(v);
        } else if (k.startsWith("keyStore.")) {
            tmt.setKeyStore(getKeyStore(tmt.getKeyStore(),
                                        k.substring("keyStore.".length()),
                                        v));
        } else if (k.startsWith("certStore")) {
            tmt.setCertStore(getCertStore(tmt.getCertStore(),
                                          k.substring("certStore.".length()),
                                          v));
        }
        return tmt;
    }

    private CertStoreType getCertStore(CertStoreType cs, String k, String v) {
        if (cs == null) {
            cs = new CertStoreType();
        }
        if ("file".equals(k)) {
            cs.setFile(v);
        } else if ("url".equals(k)) {
            cs.setUrl(v);
        } else if ("resource".equals(k)) {
            cs.setResource(v);
        }
        return cs;
    }

    private void applyProxyAuthorization(Dictionary<String, String> d, HTTPConduit c) {
        Enumeration<String> keys = d.keys();
        ProxyAuthorizationPolicy p = c.getProxyAuthorization();
        while (keys.hasMoreElements()) {
            String k = keys.nextElement();
            if (k.startsWith("proxyAuthorization.")) {
                if (p == null) {
                    p = new ProxyAuthorizationPolicy();
                    c.setProxyAuthorization(p);
                }
                String v = d.get(k);
                k = k.substring("proxyAuthorization.".length());
                
                if ("UserName".equals(k)) {
                    p.setUserName(v);
                } else if ("Password".equals(k)) {
                    p.setPassword(v);
                } else if ("Authorization".equals(k)) {
                    p.setAuthorization(v);
                } else if ("AuthorizationType".equals(k)) {
                    p.setAuthorizationType(v);
                }
            }
        }
    }

    private void applyAuthorization(Dictionary<String, String> d, HTTPConduit c) {
        Enumeration<String> keys = d.keys();
        AuthorizationPolicy p = c.getAuthorization();
        while (keys.hasMoreElements()) {
            String k = keys.nextElement();
            if (k.startsWith("authorization.")) {
                if (p == null) {
                    p = new AuthorizationPolicy();
                    c.setAuthorization(p);
                }
                String v = d.get(k);
                k = k.substring("authorization.".length());
                
                if ("UserName".equals(k)) {
                    p.setUserName(v);
                } else if ("Password".equals(k)) {
                    p.setPassword(v);
                } else if ("Authorization".equals(k)) {
                    p.setAuthorization(v);
                } else if ("AuthorizationType".equals(k)) {
                    p.setAuthorizationType(v);
                }
            }
        }
    }
    
    
    private void applyClientPolicies(Dictionary<String, String> d, HTTPConduit c) {
        Enumeration<String> keys = d.keys();
        HTTPClientPolicy p = c.getClient();
        while (keys.hasMoreElements()) {
            String k = keys.nextElement();
            if (k.startsWith("client.")) {
                if (p == null) {
                    p = new HTTPClientPolicy();
                    c.setClient(p);
                }
                String v = d.get(k);
                k = k.substring("client.".length());
                if ("ConnectionTimeout".equals(k)) {
                    p.setConnectionTimeout(Long.parseLong(v.trim()));
                } else if ("ReceiveTimeout".equals(k)) {
                    p.setReceiveTimeout(Long.parseLong(v.trim()));
                } else if ("AutoRedirect".equals(k)) {
                    p.setAutoRedirect(Boolean.parseBoolean(v.trim()));
                } else if ("MaxRetransmits".equals(k)) {
                    p.setMaxRetransmits(Integer.parseInt(v.trim()));
                } else if ("AllowChunking".equals(k)) {
                    p.setAllowChunking(Boolean.parseBoolean(v.trim()));
                } else if ("ChunkingThreshold".equals(k)) {
                    p.setChunkingThreshold(Integer.parseInt(v.trim()));
                } else if ("Connection".equals(k)) {
                    p.setConnection(ConnectionType.valueOf(v));
                } else if ("DecoupledEndpoint".equals(k)) {
                    p.setDecoupledEndpoint(v);
                } else if ("ProxyServer".equals(k)) {
                    p.setProxyServer(v);
                } else if ("ProxyServerPort".equals(k)) {
                    p.setProxyServerPort(Integer.parseInt(v.trim()));
                } else if ("ProxyServerType".equals(k)) {
                    p.setProxyServerType(ProxyServerType.fromValue(v));
                } else if ("NonProxyHosts".equals(k)) {
                    p.setNonProxyHosts(v);
                }
            }
        }
    }
    
    
    
}