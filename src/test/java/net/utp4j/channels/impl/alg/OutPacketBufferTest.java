/* Copyright 2013 Ivan Iljkic
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.utp4j.channels.impl.alg;

import net.utp4j.channels.impl.UtpTimestampedPacketDTO;
import net.utp4j.data.MicroSecondsTimeStamp;
import net.utp4j.data.UtpPacket;
import net.utp4j.data.UtpPacketUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Queue;

import static net.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class OutPacketBufferTest {

    private static final int PAYLOAD_LENGTH = 1300;

    @Test
    public void test() throws SocketException {
        MicroSecondsTimeStamp stamper = mock(MicroSecondsTimeStamp.class);
        when(stamper.timeStamp()).thenReturn(2L);
        OutPacketBuffer buffer = new OutPacketBuffer(stamper);
        buffer.setRemoteAdress(new InetSocketAddress(12345));
        buffer.setResendtimeOutMicros(2000L);
        buffer.bufferPacket(createPacket(3));
        buffer.bufferPacket(createPacket(4));
        buffer.bufferPacket(createPacket(5));
        buffer.bufferPacket(createPacket(6));
        buffer.bufferPacket(createPacket(7));
        buffer.bufferPacket(createPacket(8));
        buffer.bufferPacket(createPacket(9));
        buffer.bufferPacket(createPacket(10));


        // CHECK bytes on fly
        assertFalse(buffer.isEmpty());
        assertEquals(8 * (PAYLOAD_LENGTH + UtpPacketUtils.DEF_HEADER_LENGTH), buffer.getBytesOnfly());

        // mark 4,5,6 acked
        buffer.markPacketAcked(4, 1, false);
        buffer.markPacketAcked(5, 1, false);
        buffer.markPacketAcked(6, 1, false);
        buffer.removeAcked();

        // since 3 not yet acked, buffer should not remove packets
        assertFalse(buffer.isEmpty());
        assertEquals(8 * (PAYLOAD_LENGTH + UtpPacketUtils.DEF_HEADER_LENGTH), buffer.getBytesOnfly());

        // now packet 3 should be triggeret to resend
        Queue<UtpTimestampedPacketDTO> packetsToResend = buffer.getPacketsToResend(50);
        assertEquals(1, packetsToResend.size());
        UtpTimestampedPacketDTO three = packetsToResend.remove();
        assertEquals(3, three.utpPacket().getSequenceNumber() & 0xFFFF);

        // ack for seqNr=8,9,10 recieved,
        buffer.markPacketAcked(8, 1, false);
        buffer.markPacketAcked(9, 1, false);
        buffer.markPacketAcked(10, 1, false);

        // still no removal
        buffer.removeAcked();

        assertFalse(buffer.isEmpty());
        assertEquals(8 * (PAYLOAD_LENGTH + UtpPacketUtils.DEF_HEADER_LENGTH), buffer.getBytesOnfly());

        // 6 should be resend now, because we already resend 3
        // for the reason 3 or more packets were acked after it. next resend should be timeout
        packetsToResend = buffer.getPacketsToResend(50);
        assertEquals(1, packetsToResend.size());
        UtpTimestampedPacketDTO seven = packetsToResend.remove();

        assertEquals(7, seven.utpPacket().getSequenceNumber() & 0xFFFF);


        // seqNr=3 is now here
        buffer.markPacketAcked(3, 1, false);
        buffer.removeAcked();
        // 3,4,5,6 are gone from the buffer, 7,8,9,10 still here
        assertEquals(4 * (PAYLOAD_LENGTH + UtpPacketUtils.DEF_HEADER_LENGTH), buffer.getBytesOnfly());

        // 7 already triggered an resend because 3 or more packets past it where acked. next resend is when a timeout occures.
        packetsToResend = buffer.getPacketsToResend(50);
        assertEquals(0, packetsToResend.size());


        // ackNr=7 finally arrives
        buffer.markPacketAcked(7, 1, false);
        buffer.removeAcked();

        // nothing left in the buffer
        assertEquals(0, buffer.getBytesOnfly());
        assertTrue(buffer.isEmpty());

        // and nothing to resend
        packetsToResend = buffer.getPacketsToResend(50);
        assertTrue(packetsToResend.isEmpty());
    }


    private UtpTimestampedPacketDTO createPacket(int sequenceNumber) throws SocketException {
        UtpPacket pkt = new UtpPacket();
        pkt.setSequenceNumber(longToUshort(sequenceNumber));
        pkt.setPayload(new byte[PAYLOAD_LENGTH]);
        byte[] array = {(byte) 1};
        SocketAddress addr = new InetSocketAddress(111);
        DatagramPacket mockDgPkt = new DatagramPacket(array, 1, addr);
        UtpTimestampedPacketDTO toReturn = new UtpTimestampedPacketDTO(mockDgPkt, pkt, 1L, 0);

        return toReturn;
    }

}
