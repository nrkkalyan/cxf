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

package org.apache.cxf.jaxb.io;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.apache.cxf.common.i18n.Message;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.databinding.DataReader;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxb.JAXBDataBase;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.jaxb.JAXBEncoderDecoder;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.service.model.MessagePartInfo;

public class DataReaderImpl<T> extends JAXBDataBase implements DataReader<T> {
    private static final Logger LOG = LogUtils.getLogger(JAXBDataBinding.class);
    JAXBDataBinding databinding;
    boolean unwrapJAXBElement = true;
    ValidationEventHandler veventHandler;
    boolean setEventHandler = true;
    
    public DataReaderImpl(JAXBDataBinding binding, boolean unwrap) {
        super(binding.getContext());
        unwrapJAXBElement = unwrap;
        databinding = binding;
    }

    public Object read(T input) {
        return read(null, input);
    }
    public void setProperty(String prop, Object value) {
        if (prop.equals(JAXBDataBinding.UNWRAP_JAXB_ELEMENT)) {
            unwrapJAXBElement = Boolean.TRUE.equals(value);
        } else if (prop.equals(org.apache.cxf.message.Message.class.getName())) {
            org.apache.cxf.message.Message m = (org.apache.cxf.message.Message)value;
            veventHandler = (ValidationEventHandler)m.getContextualProperty("jaxb-validation-event-handler");
            if (veventHandler == null) {
                veventHandler = databinding.getValidationEventHandler();
            }
            setEventHandler = MessageUtils.getContextualBoolean(m, "set-jaxb-validation-event-handler", true);
            
            Object unwrapProperty = m.get(JAXBDataBinding.UNWRAP_JAXB_ELEMENT);
            if (unwrapProperty == null) {
                unwrapProperty = m.getExchange().get(JAXBDataBinding.UNWRAP_JAXB_ELEMENT);
            }
            if (unwrapProperty != null) {
                unwrapJAXBElement = Boolean.TRUE.equals(unwrapProperty);
            }
        }
    }
    private Unmarshaller createUnmarshaller() {
        try {
            Unmarshaller um = null;
            um = context.createUnmarshaller();
            if (databinding.getUnmarshallerListener() != null) {
                um.setListener(databinding.getUnmarshallerListener());
            }
            if (setEventHandler) {
                um.setEventHandler(veventHandler);
            }
            if (databinding.getUnmarshallerProperties() != null) {
                for (Map.Entry<String, Object> propEntry 
                    : databinding.getUnmarshallerProperties().entrySet()) {
                    try {
                        um.setProperty(propEntry.getKey(), propEntry.getValue());
                    } catch (PropertyException pe) {
                        LOG.log(Level.INFO, "PropertyException setting Marshaller properties", pe);
                    }
                }
            }
            um.setSchema(schema);
            um.setAttachmentUnmarshaller(getAttachmentUnmarshaller());
            return um;
        } catch (JAXBException ex) {
            if (ex instanceof javax.xml.bind.UnmarshalException) {
                javax.xml.bind.UnmarshalException unmarshalEx = (javax.xml.bind.UnmarshalException)ex;
                throw new Fault(new Message("UNMARSHAL_ERROR", LOG, unmarshalEx.getLinkedException()
                    .getMessage()), ex);
            } else {
                throw new Fault(new Message("UNMARSHAL_ERROR", LOG, ex.getMessage()), ex);
            }
        }
    }

    public Object read(MessagePartInfo part, T reader) {
        boolean honorJaxbAnnotation = false;
        if (part != null && part.getProperty("honor.jaxb.annotations") != null) { 
            honorJaxbAnnotation = (Boolean)part.getProperty("honor.jaxb.annotations");
        }
        
        Annotation[] anns = null;
       
        if (honorJaxbAnnotation) {
            anns = getJAXBAnnotation(part);
            if (anns.length > 0) {
                // RpcLit will use the JAXB Bridge to unmarshall part message when it is
                // annotated with @XmlList,@XmlAttachmentRef,@XmlJavaTypeAdapter
                // TODO:Cache the JAXBRIContext
                QName qname = new QName(null, part.getConcreteName().getLocalPart());

                return JAXBEncoderDecoder.unmarshalWithBridge(qname, 
                                                              part.getTypeClass(), 
                                                              anns, 
                                                              databinding.getContextClasses(), 
                                                              reader, 
                                                              getAttachmentUnmarshaller());
            }
        }
        //The jaxb class contains XMLGregorianCalendar field also needs JAXB Bridge
        if (part != null && part.getTypeClass() != null) {
            boolean useJAXBBridge = false;
            for (Field field : part.getTypeClass().getDeclaredFields()) {
                if (field.getType().equals(XMLGregorianCalendar.class)) {
                    useJAXBBridge = true;
                    break;
                }
            }
            if (useJAXBBridge) {
                return JAXBEncoderDecoder.unmarshalWithBridge(part.getConcreteName(), 
                                                              part.getTypeClass(),
                                                              part.getTypeClass().getAnnotations(),
                                                              databinding.getContextClasses(), 
                                                              reader,
                                                              getAttachmentUnmarshaller());
            }
        }
        
        
        return JAXBEncoderDecoder.unmarshall(createUnmarshaller(), reader, part, 
                                             unwrapJAXBElement);
    }

    public Object read(QName name, T input, Class type) {
        return JAXBEncoderDecoder.unmarshall(createUnmarshaller(), input,
                                             name, type, 
                                             unwrapJAXBElement);
    }

}
