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

package org.apache.cxf.ws.rm.soap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.w3c.dom.Element;

import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.ws.addressing.AddressingProperties;
import org.apache.cxf.ws.addressing.Names;
import org.apache.cxf.ws.rm.RM10Constants;
import org.apache.cxf.ws.rm.RMConstants;
import org.apache.cxf.ws.rm.RMContextUtils;
import org.apache.cxf.ws.rm.RMProperties;
import org.apache.cxf.ws.rm.SequenceFault;
import org.apache.cxf.ws.rm.v200702.AckRequestedType;
import org.apache.cxf.ws.rm.v200702.Identifier;
import org.apache.cxf.ws.rm.v200702.ObjectFactory;
import org.apache.cxf.ws.rm.v200702.SequenceAcknowledgement;
import org.apache.cxf.ws.rm.v200702.SequenceType;

import org.junit.Assert;
import org.junit.Test;

public class RMSoapOutInterceptorTest extends Assert {

    private static final Long ONE = new Long(1);
    private static final Long TEN = new Long(10);
    
    private SequenceType s1;
    private SequenceType s2;
    private SequenceAcknowledgement ack1;
    private SequenceAcknowledgement ack2;
    private AckRequestedType ar1;
    private AckRequestedType ar2;

    @Test
    public void testGetUnderstoodHeaders() throws Exception {
        RMSoapOutInterceptor codec = new RMSoapOutInterceptor();
        Set<QName> headers = codec.getUnderstoodHeaders();
        assertTrue("expected Sequence header", headers.contains(RM10Constants.SEQUENCE_QNAME));
        assertTrue("expected SequenceAcknowledgment header", 
                   headers.contains(RM10Constants.SEQUENCE_ACK_QNAME));
        assertTrue("expected AckRequested header", 
                   headers.contains(RM10Constants.ACK_REQUESTED_QNAME));
    }

    @Test
    public void testEncode() throws Exception {
        RMSoapOutInterceptor codec = new RMSoapOutInterceptor();
        setUpOutbound();
        SoapMessage message = setupOutboundMessage();

        // no RM headers
   
        codec.handleMessage(message);
        verifyHeaders(message, new String[] {});

        // one sequence header

        message = setupOutboundMessage();        
        RMProperties rmps = RMContextUtils.retrieveRMProperties(message, true);     
        rmps.setSequence(s1);
        codec.encode(message);
        verifyHeaders(message, new String[] {RMConstants.SEQUENCE_NAME});

        // one acknowledgment header

        message = setupOutboundMessage(); 
        rmps = RMContextUtils.retrieveRMProperties(message, true);          
        Collection<SequenceAcknowledgement> acks = new ArrayList<SequenceAcknowledgement>();
        acks.add(ack1);
        rmps.setAcks(acks);        
        codec.encode(message);
        verifyHeaders(message, new String[] {RMConstants.SEQUENCE_ACK_NAME});

        // two acknowledgment headers

        message = setupOutboundMessage();
        rmps = RMContextUtils.retrieveRMProperties(message, true);        
        acks.add(ack2);
        rmps.setAcks(acks);
        codec.encode(message);
        verifyHeaders(message, new String[] {RMConstants.SEQUENCE_ACK_NAME, 
                                             RMConstants.SEQUENCE_ACK_NAME});

        // one ack requested header

        message = setupOutboundMessage();
        rmps = RMContextUtils.retrieveRMProperties(message, true);        
        Collection<AckRequestedType> requested = new ArrayList<AckRequestedType>();
        requested.add(ar1);
        rmps.setAcksRequested(requested);
        codec.encode(message);
        verifyHeaders(message, new String[] {RMConstants.ACK_REQUESTED_NAME});

        // two ack requested headers

        message = setupOutboundMessage();
        rmps = RMContextUtils.retrieveRMProperties(message, true);         
        requested.add(ar2);
        rmps.setAcksRequested(requested);
        codec.encode(message);
        verifyHeaders(message, new String[] {RMConstants.ACK_REQUESTED_NAME, 
                                             RMConstants.ACK_REQUESTED_NAME});
    }
    
    @Test
    public void testEncodeFault() throws Exception {
        RMSoapOutInterceptor codec = new RMSoapOutInterceptor();
        setUpOutbound();
        SoapMessage message = setupOutboundFaultMessage();

        // no RM headers and no fault
   
        codec.encode(message);
        verifyHeaders(message, new String[] {});

        // fault is not a SoapFault

        message = setupOutboundFaultMessage();
        assertTrue(MessageUtils.isFault(message));
        Exception ex = new RuntimeException("");
        message.setContent(Exception.class, ex);      
        codec.encode(message);
        verifyHeaders(message, new String[] {});
        
        // fault is a SoapFault but does not have a SequenceFault cause

        message = setupOutboundFaultMessage();
        SoapFault f = new SoapFault("REASON", RM10Constants.UNKNOWN_SEQUENCE_FAULT_QNAME);
        message.setContent(Exception.class, f);      
        codec.encode(message);
        verifyHeaders(message, new String[] {});

        // fault is a SoapFault and has a SequenceFault cause
        
        message = setupOutboundFaultMessage();
        SequenceFault sf = new SequenceFault("REASON");
        sf.setFaultCode(RM10Constants.UNKNOWN_SEQUENCE_FAULT_QNAME);
        Identifier sid = new Identifier();
        sid.setValue("SID");
        sf.setSender(true);
        f.initCause(sf);
        message.setContent(Exception.class, f);      
        codec.encode(message);
        verifyHeaders(message, new String[] {RMConstants.SEQUENCE_FAULT_NAME});

    }

    private void setUpOutbound() {
        ObjectFactory factory = new ObjectFactory();
        s1 = factory.createSequenceType();
        Identifier sid = factory.createIdentifier();
        sid.setValue("sequence1");
        s1.setIdentifier(sid);
        s1.setMessageNumber(ONE);
        s2 = factory.createSequenceType();
        sid = factory.createIdentifier();
        sid.setValue("sequence2");
        s2.setIdentifier(sid);
        s2.setMessageNumber(TEN);

        ack1 = factory.createSequenceAcknowledgement();
        SequenceAcknowledgement.AcknowledgementRange r = 
            factory.createSequenceAcknowledgementAcknowledgementRange();
        r.setLower(ONE);
        r.setUpper(ONE);
        ack1.getAcknowledgementRange().add(r);
        ack1.setIdentifier(s1.getIdentifier());

        ack2 = factory.createSequenceAcknowledgement();
        r = factory.createSequenceAcknowledgementAcknowledgementRange();
        r.setLower(ONE);
        r.setUpper(TEN);
        ack2.getAcknowledgementRange().add(r);
        ack2.setIdentifier(s2.getIdentifier());

        ar1 = factory.createAckRequestedType();
        ar1.setIdentifier(s1.getIdentifier());

        ar2 = factory.createAckRequestedType();
        ar2.setIdentifier(s2.getIdentifier());
    }

    private SoapMessage setupOutboundMessage() throws Exception {
        Exchange ex = new ExchangeImpl();
        Message message = new MessageImpl();
        SoapMessage soapMessage = new SoapMessage(message);
        RMProperties rmps = new RMProperties();
        rmps.exposeAs(RM10Constants.NAMESPACE_URI);
        RMContextUtils.storeRMProperties(soapMessage, rmps, true);
        AddressingProperties maps = new AddressingProperties();
        RMContextUtils.storeMAPs(maps, soapMessage, true, false);
        ex.setOutMessage(soapMessage);
        soapMessage.setExchange(ex);        
        MessageFactory factory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
        SOAPMessage soap = factory.createMessage();
        QName bodyName = new QName("http://cxf.apache.org", "dummy", "d");
        soap.getSOAPBody().addBodyElement(bodyName);
        soapMessage.setContent(SOAPMessage.class, soap);
        return soapMessage;
    }
    
    private SoapMessage setupOutboundFaultMessage() throws Exception {
        Exchange ex = new ExchangeImpl();
        Message message = new MessageImpl();
        RMProperties rmps = new RMProperties();
        rmps.exposeAs(RM10Constants.NAMESPACE_URI);
        RMContextUtils.storeRMProperties(message, rmps, false);
        AddressingProperties maps = new AddressingProperties();
        RMContextUtils.storeMAPs(maps, message, false, false);
        ex.setInMessage(message);
        message = new MessageImpl();
        SoapMessage soapMessage = new SoapMessage(message);         
        ex.setOutFaultMessage(soapMessage);
        soapMessage.setExchange(ex);        
        MessageFactory factory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
        SOAPMessage soap = factory.createMessage();
        soap.getSOAPBody().addFault();
        soapMessage.setContent(SOAPMessage.class, soap);
        return soapMessage;
    }

    private void verifyHeaders(SoapMessage message, String... names) {
        SOAPMessage content = message.getContent(SOAPMessage.class);

        // check all expected headers are present

        for (String name : names) {
            boolean found = false;
            try {
                Iterator<?> elems = content.getSOAPHeader().getChildElements();
                while (elems.hasNext()) {
                    Element elem = (Element)elems.next();
                    String namespace = elem.getNamespaceURI();
                    String localName = elem.getLocalName();
                    if (RM10Constants.NAMESPACE_URI.equals(namespace)
                        && localName.equals(name)) {
                        found = true;
                        break;
                    } else if (Names.WSA_NAMESPACE_NAME.equals(namespace)
                        && localName.equals(name)) {
                        found = true;
                        break;
                    }
                }
            } catch (SOAPException e) { /* failure will result in not found */ }
            assertTrue("Could not find header element " + name, found);
        }

        // no other headers should be present
        try {
            Iterator<?> elems = content.getSOAPHeader().getChildElements();
            while (elems.hasNext()) {
                Element elem = (Element)elems.next();
                String namespace = elem.getNamespaceURI();
                String localName = elem.getLocalName();
                assertTrue(RM10Constants.NAMESPACE_URI.equals(namespace) 
                    || Names.WSA_NAMESPACE_NAME.equals(namespace));
                boolean found = false;
                for (String name : names) {
                    if (localName.equals(name)) {
                        found = true;
                        break;
                    }
                }
                assertTrue("Unexpected header element " + localName, found);
            }
        } catch (SOAPException e) { /* failure would have been caught before */ }
    }
}
