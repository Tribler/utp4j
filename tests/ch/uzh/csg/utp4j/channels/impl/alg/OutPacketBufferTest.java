package ch.uzh.csg.utp4j.channels.impl.alg;

import static org.junit.Assert.*;


import java.net.SocketException;
import java.util.Queue;

import org.junit.Test;
import org.junit.runner.RunWith;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

import static org.mockito.Mockito.*;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.*;


@RunWith(org.mockito.runners.MockitoJUnitRunner.class)
public class OutPacketBufferTest {
	
	private static int PAYLOAD_LENGTH = 1300;

	@Test
	public void test() throws SocketException {
		MicroSecondsTimeStamp stamper = mock(MicroSecondsTimeStamp.class);
		when(stamper.timeStamp()).thenReturn(2L);
		OutPacketBuffer buffer = new OutPacketBuffer(stamper);
		buffer.setTimeOutMicroSec(2000L);
		buffer.bufferPacket(createPacket(3));
		buffer.bufferPacket(createPacket(4));
		buffer.bufferPacket(createPacket(5));
		buffer.bufferPacket(createPacket(6));
		buffer.bufferPacket(createPacket(7));
		buffer.bufferPacket(createPacket(8));
		buffer.bufferPacket(createPacket(9));
		buffer.bufferPacket(createPacket(10));

		
		// CHECK bytes on fly
		assertEquals(false, buffer.isEmpty());
		assertEquals(8*(PAYLOAD_LENGTH + UtpPacketUtils.DEF_HEADER_LENGTH), buffer.getBytesOnfly());
		
		// mark 4,5,6 acked
		buffer.markPacketAcked(4, 1);
		buffer.markPacketAcked(5, 1);
		buffer.markPacketAcked(6, 1);
		buffer.removeAcked();
		
		// since 3 not yet acked, buffer should not remove packets
		assertEquals(false, buffer.isEmpty());
		assertEquals(8*(PAYLOAD_LENGTH + UtpPacketUtils.DEF_HEADER_LENGTH), buffer.getBytesOnfly());
		
		// now packet 3 should be triggeret to resend
		Queue<UtpTimestampedPacketDTO> packetsToResend = buffer.getPacketsToResend();
		assertEquals(1, packetsToResend.size());
		UtpTimestampedPacketDTO three = packetsToResend.remove();
		assertEquals(3, three.utpPacket().getSequenceNumber() & 0xFFFF);
		
		// ack for seqNr=8,9,10 recieved, 
		buffer.markPacketAcked(8, 1);
		buffer.markPacketAcked(9, 1);
		buffer.markPacketAcked(10, 1);
		
		// still no removal
		buffer.removeAcked();

		assertEquals(false, buffer.isEmpty());
		assertEquals(8*(PAYLOAD_LENGTH + UtpPacketUtils.DEF_HEADER_LENGTH), buffer.getBytesOnfly());
		
		// 6 should be resend now, because we already resend 3 
		// for the reason 3 or more packets were acked after it. next resend should be timeout
		packetsToResend = buffer.getPacketsToResend();
		assertEquals(1, packetsToResend.size());
		UtpTimestampedPacketDTO seven = packetsToResend.remove();
		
		assertEquals(7, seven.utpPacket().getSequenceNumber() & 0xFFFF);

		
		// seqNr=3 is now here
		buffer.markPacketAcked(3, 1);
		buffer.removeAcked();
		// 3,4,5,6 are gone from the buffer, 7,8,9,10 still here
		assertEquals(4*(PAYLOAD_LENGTH + UtpPacketUtils.DEF_HEADER_LENGTH), buffer.getBytesOnfly());
		
		// 7 already triggered an resend because 3 or more packets past it where acked. next resend is when a timeout occures. 
		packetsToResend = buffer.getPacketsToResend();
		assertEquals(0, packetsToResend.size());

		
		// ackNr=7 finally arrives
		buffer.markPacketAcked(7, 1);
		buffer.removeAcked();
		
		// nothing left in the buffer
		assertEquals(0, buffer.getBytesOnfly());
		assertEquals(true, buffer.isEmpty());
			
		// and nothing to resend
		packetsToResend = buffer.getPacketsToResend();
		assertEquals(true, packetsToResend.isEmpty());
	}
	
	
	private UtpTimestampedPacketDTO createPacket(int sequenceNumber) {
		UtpPacket pkt = new UtpPacket();
		pkt.setSequenceNumber(longToUshort(sequenceNumber));
		pkt.setPayload(new byte[PAYLOAD_LENGTH]);
		UtpTimestampedPacketDTO toReturn = new UtpTimestampedPacketDTO(null, pkt, 1L, 0);
		
		return toReturn;
	}

}
