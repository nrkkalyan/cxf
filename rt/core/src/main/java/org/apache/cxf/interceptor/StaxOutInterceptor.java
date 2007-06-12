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

package org.apache.cxf.interceptor;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.staxutils.StaxUtils;


/**
 * Creates an XMLStreamReader from the InputStream on the Message.
 */
public class StaxOutInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(StaxOutInterceptor.class);
    private static Map<Object, XMLOutputFactory> factories = new HashMap<Object, XMLOutputFactory>();

    private StaxOutEndingInterceptor ending = new StaxOutEndingInterceptor();
    
    public StaxOutInterceptor() {
        super(Phase.PRE_STREAM);
        addAfter(AttachmentOutInterceptor.class.getName());
    }

    public void handleMessage(Message message) {
        OutputStream os = message.getContent(OutputStream.class);
        XMLStreamWriter writer = message.getContent(XMLStreamWriter.class);

        if (os == null || writer != null) {
            return;
        }
        // assert os != null;

        // TODO: where does encoding constant go?
        String encoding = (String)message.get(Message.ENCODING);
        if (encoding == null && message.getExchange().getInMessage() != null) {
            encoding = (String) message.getExchange().getInMessage().get(Message.ENCODING);
            message.put(Message.ENCODING, encoding);
        }
        
        if (encoding == null) {
            encoding = "UTF-8";
            message.put(Message.ENCODING, encoding);
        }
        
        try {
            writer = getXMLOutputFactory(message).createXMLStreamWriter(os, encoding);
        } catch (XMLStreamException e) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("STREAM_CREATE_EXC", BUNDLE), e);
        }
        message.setContent(XMLStreamWriter.class, writer);

        // Add a final interceptor to write end elements
        message.getInterceptorChain().add(ending);
    }

    public static XMLOutputFactory getXMLOutputFactory(Message m) throws Fault {
        Object o = m.getContextualProperty(XMLOutputFactory.class.getName());
        if (o instanceof XMLOutputFactory) {
            return (XMLOutputFactory)o;
        } else if (o != null) {
            XMLOutputFactory xif = (XMLOutputFactory)factories.get(o);
            if (xif == null) {
                Class cls;
                if (o instanceof Class) {
                    cls = (Class)o;
                } else if (o instanceof String) {
                    try {
                        cls = ClassLoaderUtils.loadClass((String)o, StaxInInterceptor.class);
                    } catch (ClassNotFoundException e) {
                        throw new Fault(e);
                    }
                } else {
                    throw new Fault(new org.apache.cxf.common.i18n.Message("INVALID_INPUT_FACTORY", 
                                                                           BUNDLE, o));
                }

                try {
                    xif = (XMLOutputFactory)(cls.newInstance());
                    factories.put(o, xif);
                } catch (InstantiationException e) {
                    throw new Fault(e);
                } catch (IllegalAccessException e) {
                    throw new Fault(e);
                }
            }
            return xif;
        } else {
            return StaxUtils.getXMLOutputFactory();
        }
    }
    
    public class StaxOutEndingInterceptor extends AbstractPhaseInterceptor<Message> {
        public StaxOutEndingInterceptor() {
            super(Phase.PRE_STREAM_ENDING);
            getAfter().add(AttachmentOutInterceptor.AttachmentOutEndingInterceptor.class.getName());
        }

        public void handleMessage(Message message) throws Fault {
            try {
                XMLStreamWriter xtw = message.getContent(XMLStreamWriter.class);
                if (xtw != null) {
                    xtw.writeEndDocument();
                    xtw.close();
                }
            } catch (XMLStreamException e) {
                throw new Fault(new org.apache.cxf.common.i18n.Message("STAX_WRITE_EXC", BUNDLE), e);
            }
        }

    }    
}
