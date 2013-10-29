package ch.uzh.csg.utp4j.channels.impl.alg;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Queue;

import org.junit.Test;
import org.junit.runner.RunWith;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.SelectiveAckHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

@RunWith(org.mockito.runners.MockitoJUnitRunner.class)
public class UtpAlgorithmTest {

	/**
	 * Packet size testing, dynamic, linear
	 */
	@Test
	public void testDynamicPacketSize() {
		int c_target = 100000;		
		UtpAlgConfiguration.PACKET_SIZE_MODE = PacketSizeModus.DYNAMIC_LINEAR;
		UtpAlgConfiguration.C_CONTROL_TARGET_MICROS = c_target;
		MinimumDelay mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getRecentAverageDelay()).thenReturn((long) c_target/2);
		
		/* when delay is 50ms, then packetsize = 
		 * (delay/max_delay)*(max_packet-min_packet)+min_packet */
		MicroSecondsTimeStamp stamper = new MicroSecondsTimeStamp();
		UtpAlgorithm alg = new UtpAlgorithm(stamper, new InetSocketAddress(51235));
		alg.setMinDelay(mockMinDelay);
		assertEquals(811, alg.sizeOfNextPacket());
		
		mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getRecentAverageDelay()).thenReturn((long) c_target);
		alg.setMinDelay(mockMinDelay);
		assertEquals(150, alg.sizeOfNextPacket());
		
		mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getRecentAverageDelay()).thenReturn(0L);
		alg.setMinDelay(mockMinDelay);
		assertEquals(1472, alg.sizeOfNextPacket());
		
		mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getRecentAverageDelay()).thenReturn((long) c_target/10*3);
		alg.setMinDelay(mockMinDelay);
		assertEquals(1076, alg.sizeOfNextPacket());
		
		mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getRecentAverageDelay()).thenReturn((long) c_target/10*7);
		alg.setMinDelay(mockMinDelay);
		assertEquals(547, alg.sizeOfNextPacket());
		
		mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getRecentAverageDelay()).thenReturn((long) c_target/10*12);
		alg.setMinDelay(mockMinDelay);
		assertEquals(150, alg.sizeOfNextPacket());
	}

	@Test
	public void testAcking() throws SocketException {
		UtpAlgConfiguration.AUTO_ACK_SMALLER_THAN_ACK_NUMBER = true;
		UtpAlgConfiguration.MIN_SKIP_PACKET_BEFORE_RESEND = 3;
		
		MicroSecondsTimeStamp stamper = mock(MicroSecondsTimeStamp.class);
		when(stamper.timeStamp()).thenReturn(0L);
		
		UtpAlgorithm algorithm = new UtpAlgorithm(stamper,  new InetSocketAddress(51235));
		ByteBuffer bufferMock = mock(ByteBuffer.class);
		algorithm.setByteBuffer(bufferMock);
		
		// Add some packets, 4...14
		UtpTimestampedPacketDTO pkt = createPacket(3);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(4);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(5);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(6);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(7);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(8);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(9);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(10);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(11);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(12);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(13);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(14);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		
		// now 11 unacked packets: 3,...,13,14 
		// ack with following: ACK:5, SACK: 7,8,9,10,11,12,13,14 -> should trigger resend 6
		// because 3,4,5 is beeing autoacked, 7,8,9,10,11,12,13,14 beeing acked by selective ack. 
		// ACK:5,SACK:7,8,9,10,11,12,13,14 bitpattern: 11111111 -> least significant bit is always ACK+2
		// in this case its 7. 
		byte[] selAck = {(byte) 255, (byte) 0, (byte) 0, (byte) 0};
		UtpTimestampedPacketDTO ackPacket = createSelAckPacket(5, selAck);
		algorithm.ackRecieved(ackPacket);
		
		// now 3,4,5 should be removed
		algorithm.removeAcked();
		String leftElements = algorithm.getLeftElements();
		assertEquals("6 7 8 9 10 11 12 13 14", leftElements);
		
		// 3 past 6 are acked, trigger an resend of 6
		Queue<DatagramPacket> packetsToResend = algorithm.getPacketsToResend();
		assertEquals(1, packetsToResend.size());
		assertEquals(6, (UtpPacketUtils.extractUtpPacket(packetsToResend.remove()).getSequenceNumber() & 0xFFFF));
		
		// 6 beeing acked now. 
		UtpTimestampedPacketDTO ack6 = createSelAckPacket(6, null);
		// no extension... 
		ack6.utpPacket().setFirstExtension((byte) 0);
		ack6.utpPacket().setExtensions(null);
		
		algorithm.ackRecieved(ack6);
		algorithm.removeAcked();
		
		// everything is now acked
		leftElements = algorithm.getLeftElements();
		assertEquals("", leftElements);
		
		// no packets to resend
		packetsToResend = algorithm.getPacketsToResend();
		assertEquals(0, packetsToResend.size());
		
		
	}
	
	private UtpTimestampedPacketDTO createSelAckPacket(int i, byte[] selAck) throws SocketException {
		UtpTimestampedPacketDTO ack = createPacket(2); 
		ack.utpPacket().setAckNumber((short) (i & 0xFFFF));
		
		SelectiveAckHeaderExtension selAckExtension = new SelectiveAckHeaderExtension();
		selAckExtension.setBitMask(selAck);
		selAckExtension.setNextExtension((byte) 0);
		ack.utpPacket().setFirstExtension(UtpPacketUtils.SELECTIVE_ACK);
		
		SelectiveAckHeaderExtension[] extensions = { selAckExtension };
		ack.utpPacket().setExtensions(extensions);
		
		return ack;
		
	}
	
	@Test
	public void testResendNoTriggerReduceWindow() throws SocketException {
		UtpAlgConfiguration.AUTO_ACK_SMALLER_THAN_ACK_NUMBER = true;
		UtpAlgConfiguration.MIN_SKIP_PACKET_BEFORE_RESEND = 3;
		
		int maxWindow = 100000;
		
		MicroSecondsTimeStamp stamper = mock(MicroSecondsTimeStamp.class);
		when(stamper.timeStamp()).thenReturn(0L);
		
		UtpAlgorithm algorithm = new UtpAlgorithm(stamper,  new InetSocketAddress(51235));
		ByteBuffer bufferMock = mock(ByteBuffer.class);
		algorithm.setByteBuffer(bufferMock);
		
		UtpTimestampedPacketDTO pkt = createPacket(5);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(6);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(7);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(8);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		pkt = createPacket(9);
		algorithm.markPacketOnfly(pkt.utpPacket(), pkt.dataGram());
		
		// packets 5,6,7,8,9 on fly
		// now will ACK:5, SACK: 7,8,9 
		// SACK bits as follow: 00000111 000... ... ...000
		// this means, 3 packets past 6 have been acked. 
		// should trigger fast resend of 6. 
		// maxWindow should not be multiplied with 0.5.
		byte[] selAck = { (byte) 7, 0, 0, 0};
		UtpTimestampedPacketDTO sack = createSelAckPacket(5, selAck);
		algorithm.ackRecieved(sack);		
		algorithm.setMaxWindow(maxWindow);
		
		Queue<DatagramPacket> queue = algorithm.getPacketsToResend();
		assertEquals(1, queue.size());
		
		assertEquals(maxWindow, algorithm.getMaxWindow());
		
	}
	
	@Test
	public void testPacketSending() throws SocketException {
		MicroSecondsTimeStamp stamper = mock(MicroSecondsTimeStamp.class);
		when(stamper.timeStamp()).thenReturn(0L);
		UtpAlgorithm algorithm = new UtpAlgorithm(stamper,  new InetSocketAddress(51235));
		UtpAlgConfiguration.SEND_IN_BURST = true;
		UtpAlgConfiguration.MAX_BURST_SEND = 3;
		
		int packetLength = 1000;
		
		// make room for 10 packets. 
		algorithm.setMaxWindow(packetLength*10);
		
		// mark 5 packets on fly, will be ~5100 bytes of currentWindow
		UtpTimestampedPacketDTO pkt5 = createPacket(5, packetLength);
		UtpTimestampedPacketDTO pkt6 = createPacket(6, packetLength);
		UtpTimestampedPacketDTO pkt7 = createPacket(7, packetLength);
		UtpTimestampedPacketDTO pkt8 = createPacket(8, packetLength);
		UtpTimestampedPacketDTO pkt9 = createPacket(9, packetLength);

		algorithm.markPacketOnfly(pkt5.utpPacket(), pkt5.dataGram());
		algorithm.markPacketOnfly(pkt6.utpPacket(), pkt6.dataGram());
		algorithm.markPacketOnfly(pkt7.utpPacket(), pkt7.dataGram());
		algorithm.markPacketOnfly(pkt8.utpPacket(), pkt8.dataGram());
		algorithm.markPacketOnfly(pkt9.utpPacket(), pkt9.dataGram());
		
		assertEquals(5*(UtpPacketUtils.DEF_HEADER_LENGTH + packetLength), algorithm.getCurrentWindow());
		
		// our current window is smaller than max window. MAX_BURST_SEND times invocating should trigger a true
		for (int i = 0; i < UtpAlgConfiguration.MAX_BURST_SEND; i++) {
			assertEquals(true, algorithm.canSendNextPacket());
		}
		
		// now we cannot send a packet anymore
		assertEquals(false, algorithm.canSendNextPacket());
		
		// now again we can send 3 packets... 
		for (int i = 0; i < UtpAlgConfiguration.MAX_BURST_SEND; i++) {
			assertEquals(true, algorithm.canSendNextPacket());
		}
		// and now false. 
		assertEquals(false, algorithm.canSendNextPacket());
		
		// lets reduce maxwindow to 4* packet length, no packets can be send now.
		algorithm.setMaxWindow(packetLength * 4);
		for (int i = 0; i < UtpAlgConfiguration.MAX_BURST_SEND; i++) {
			assertEquals(false, algorithm.canSendNextPacket());
		} 
		
		// we still cannot send packets...
		for (int i = 0; i < UtpAlgConfiguration.MAX_BURST_SEND; i++) {
			assertEquals(false, algorithm.canSendNextPacket());
		} 
		
		// increase max window again. 
		algorithm.setMaxWindow(10*packetLength);
		
		// send 3 packets in one burst. 
		for (int i = 0; i < UtpAlgConfiguration.MAX_BURST_SEND; i++) {
			assertEquals(true, algorithm.canSendNextPacket());
		} 
		
		// current burst full, next invocation should be false. 
		assertEquals(false, algorithm.canSendNextPacket());
		
	}
	
	private UtpTimestampedPacketDTO createPacket(int sequenceNumber, int packetLength) throws SocketException {
		UtpPacket pkt = new UtpPacket();
		pkt.setSequenceNumber(longToUshort(sequenceNumber));
		pkt.setPayload(new byte[packetLength]);
		SocketAddress addr = new InetSocketAddress(111);
		DatagramPacket mockDgPkt = new DatagramPacket(pkt.toByteArray(), 1, addr);
		UtpTimestampedPacketDTO toReturn = new UtpTimestampedPacketDTO(mockDgPkt, pkt, 1L, 0);
		
		return toReturn;
	}


	private UtpTimestampedPacketDTO createPacket(int sequenceNumber) throws SocketException {
		return createPacket(sequenceNumber, 1);
	}
	
}
