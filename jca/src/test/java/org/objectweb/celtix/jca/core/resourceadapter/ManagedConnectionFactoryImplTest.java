package org.objectweb.celtix.jca.core.resourceadapter;


import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.security.auth.Subject;

import junit.framework.TestCase;

import org.easymock.classextension.EasyMock;

public class ManagedConnectionFactoryImplTest extends TestCase {
    DummyManagedConnectionFactoryImpl mcf = new DummyManagedConnectionFactoryImpl();

    public ManagedConnectionFactoryImplTest(String name) {
        super(name);
    }

    public void testManagedConnectionFactoryImplInstanceOfResourceBean() throws Exception {
        assertNotNull("mcf is not null", mcf);
        assertTrue("ManagedConnectionFactoryImpl is ResourceBean", mcf instanceof ResourceBean);
    }

    @SuppressWarnings("unchecked")
    public void testMatchConnectionSameConnectioRequestInfoNotBound() throws Exception {
        Subject subject = null;
        Set connectionSet = new HashSet<AbstractManagedConnectionImpl>();
        ConnectionRequestInfo cri = new DummyConnectionRequestInfo();
        DummyManagedConnectionImpl con1 = new DummyManagedConnectionImpl(mcf, cri, subject);
        connectionSet.add(con1);

        ManagedConnection mcon = mcf.matchManagedConnections(connectionSet, subject, cri);
        assertEquals(con1, mcon);
    }

    @SuppressWarnings("unchecked")
    public void testMatchConnectionSameConnectioRequestInfoBound() throws Exception {
        Subject subject = null;
        Set connectionSet = new HashSet();
        ConnectionRequestInfo cri = new DummyConnectionRequestInfo();
        DummyManagedConnectionImpl con1 = new DummyManagedConnectionImpl(mcf, cri, subject);
        con1.setBound(true);
        connectionSet.add(con1);

        ManagedConnection mcon = mcf.matchManagedConnections(connectionSet, subject, cri);
        assertEquals(con1, mcon);
    }

    @SuppressWarnings("unchecked")
    public void testMatchConnectionDifferentConnectioRequestInfoNotBound() throws Exception {
        ConnectionRequestInfo cri1 = new DummyConnectionRequestInfo();
        ConnectionRequestInfo cri2 = new DummyConnectionRequestInfo();

        Subject subject = null;
        assertTrue("request info object are differnt", cri1 != cri2);

        Set connectionSet = new HashSet();
        DummyManagedConnectionImpl con1 = new DummyManagedConnectionImpl(mcf, cri1, subject);
        connectionSet.add(con1);

        ManagedConnection mcon = mcf.matchManagedConnections(connectionSet, subject, cri2);
        assertEquals("incorrect connection returned", con1, mcon);
    }

    @SuppressWarnings("unchecked")
    public void testMatchConnectionDifferentConnectioRequestInfoBound() throws Exception {
        ConnectionRequestInfo cri1 = new DummyConnectionRequestInfo();
        ConnectionRequestInfo cri2 = new DummyConnectionRequestInfo();

        Subject subject = null;

        assertTrue("request info object are differnt", cri1 != cri2);

        Set connectionSet = new HashSet();
        DummyManagedConnectionImpl con1 = new DummyManagedConnectionImpl(mcf, cri1, subject);
        con1.setBound(true);
        connectionSet.add(con1);

        ManagedConnection mcon = mcf.matchManagedConnections(connectionSet, subject, cri2);
        assertTrue("should not get a match", mcon == null);
    }

    @SuppressWarnings("unchecked")
    public void testMatchConnectionInvalidatedWithSameConnectioRequestInfo() throws Exception {
        Subject subject = null;
        Set connectionSet = new HashSet();
        ConnectionRequestInfo cri = new DummyConnectionRequestInfo();

        DummyManagedConnectionImpl con1 = new DummyManagedConnectionImpl(mcf, cri, subject);
        con1.setBound(true);
        con1.setCon(connectionSet);
        connectionSet.add(con1);

        ManagedConnection mcon = mcf.matchManagedConnections(connectionSet, subject, cri);
        assertTrue("Connection must be null", mcon == null);
    }

    public void testGetSetLogWriter() throws Exception {
        PrintWriter writer = EasyMock.createMock(PrintWriter.class); 
        writer.write(EasyMock.isA(String.class));
        EasyMock.expectLastCall().anyTimes();
        writer.flush();
        EasyMock.expectLastCall().anyTimes();
        writer.close();
        EasyMock.expectLastCall().anyTimes();
        // the write could be call lots of time
        EasyMock.replay(writer);
        mcf.setLogWriter(writer);
        assertTrue(mcf.getLogWriter() == writer);
        EasyMock.verify(writer);
    }

    public void testSetNullLogWriter() throws Exception {
        try {
            mcf.setLogWriter(null);
            fail("expect ex on null log writer arg");
        } catch (IllegalArgumentException expected) {
            //do nothing here
        }
    }
}

class DummyConnectionRequestInfo implements ConnectionRequestInfo {
}

class DummyManagedConnectionFactoryImpl extends AbstractManagedConnectionFactoryImpl {

    private static final long serialVersionUID = -218445259745278972L;

    public DummyManagedConnectionFactoryImpl() {
    }

    public DummyManagedConnectionFactoryImpl(Properties p) {
        super(p);
    }

    public Object createConnectionFactory(ConnectionManager connMgr) throws ResourceException {
        return null;
    }

    public Object createConnectionFactory() throws ResourceException {
        return null;
    }

    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo connReqInfo)
        throws ResourceException {
        return null;
    }

    public void close() throws ResourceAdapterInternalException {
    }

    protected void validateReference(AbstractManagedConnectionImpl conn, Subject subject)
        throws ResourceAdapterInternalException {
        boolean valid = true;

        try {
            if (conn.getConnection(null, null) != null) {
                valid = false;
            }
        } catch (ResourceException ignored) {
            // do nothing here
        }

        if (!valid) {
            throw new ResourceAdapterInternalException("invalid");
        }
    }

    public void setResourceAdapter(javax.resource.spi.ResourceAdapter ra) {
    }

    public javax.resource.spi.ResourceAdapter getResourceAdapter() {
        return null;
    }
}
