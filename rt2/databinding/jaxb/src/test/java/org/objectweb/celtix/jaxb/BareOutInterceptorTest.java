package org.objectweb.celtix.jaxb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.objectweb.celtix.interceptors.BareOutInterceptor;
import org.objectweb.celtix.message.Message;
import org.objectweb.celtix.service.model.BindingOperationInfo;
import org.objectweb.celtix.staxutils.DepthXMLStreamReader;
import org.objectweb.celtix.staxutils.StaxUtils;
import org.objectweb.hello_world_soap_http.types.GreetMe;
import org.objectweb.hello_world_soap_http.types.GreetMeResponse;


public class BareOutInterceptorTest extends TestBase {

    private ByteArrayOutputStream baos;
    private XMLStreamWriter writer;
    BareOutInterceptor interceptor;
    
    public void setUp() throws Exception {
        super.setUp();
        
        interceptor = new BareOutInterceptor();
        baos =  new ByteArrayOutputStream();
        writer = getXMLStreamWriter(baos);
        message.setContent(XMLStreamWriter.class, writer);
        message.getExchange().put(BindingOperationInfo.class.getName(), operation);
    }

    public void tearDown() throws Exception {
        baos.close();
    }

    public void testWriteOutbound() throws Exception {
        GreetMeResponse greetMe = new GreetMeResponse();
        greetMe.setResponseType("responseType");
        
        message.setContent(Object.class, Arrays.asList(greetMe));

        interceptor.handleMessage(message);

        writer.close();
        
        assertNull(message.getContent(Exception.class));

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        //System.err.println(baos.toString());
        XMLStreamReader xr = StaxUtils.createXMLStreamReader(bais);
        DepthXMLStreamReader reader = new DepthXMLStreamReader(xr);
        StaxUtils.toNextElement(reader);
        assertEquals(new QName("http://objectweb.org/hello_world_soap_http/types", "greetMeResponse"),
                     reader.getName());
        
        StaxUtils.nextEvent(reader);
        StaxUtils.toNextElement(reader);
        assertEquals(new QName("http://objectweb.org/hello_world_soap_http/types", "responseType"),
                     reader.getName());
    }

    public void testWriteInbound() throws Exception {
        GreetMe greetMe = new GreetMe();
        greetMe.setRequestType("requestType");
        
        message.setContent(Object.class, Arrays.asList(greetMe));
        message.put(Message.INBOUND_MESSAGE, Message.INBOUND_MESSAGE);
        
        interceptor.handleMessage(message);
        
        writer.close();
        
        assertNull(message.getContent(Exception.class));

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        XMLStreamReader xr = StaxUtils.createXMLStreamReader(bais);
        DepthXMLStreamReader reader = new DepthXMLStreamReader(xr);
        StaxUtils.toNextElement(reader);
        assertEquals(new QName("http://objectweb.org/hello_world_soap_http/types", "greetMe"),
                     reader.getName());
        
        StaxUtils.nextEvent(reader);
        StaxUtils.toNextElement(reader);
        assertEquals(new QName("http://objectweb.org/hello_world_soap_http/types", "requestType"),
                     reader.getName());
    }
}
