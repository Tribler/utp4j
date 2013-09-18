package ch.uzh.csg.utp4j.channels.impl.read;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUbyte;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Queue;

import org.junit.Test;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.data.SelectiveAckHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpPacket;

public class SkippedPacketBufferTest {
	
	
	private UtpTimestampedPacketDTO createPacket(int seqNr) {
		UtpPacket u = new UtpPacket();
		u.setSequenceNumber(longToUshort(seqNr));
		UtpTimestampedPacketDTO pkt = new UtpTimestampedPacketDTO(null, u, 0L, 0);
		return pkt;
	}
	
	/**
	 * Test for correct order
	 * @throws IOException 
	 */
	@Test
	public void testCorrectOrder() throws IOException {
		SkippedPacketBuffer buffer = new SkippedPacketBuffer();
		buffer.setExpectedSequenceNumber(5);
		
		buffer.bufferPacket(createPacket(8));
		buffer.bufferPacket(createPacket(7));
		buffer.bufferPacket(createPacket(9));
		buffer.bufferPacket(createPacket(6));
		
		Queue<UtpTimestampedPacketDTO> allPackets = buffer.getAllUntillNextMissing();
		
		UtpTimestampedPacketDTO six = allPackets.remove();
		UtpTimestampedPacketDTO seven = allPackets.remove();
		UtpTimestampedPacketDTO eight = allPackets.remove();
		UtpTimestampedPacketDTO nine = allPackets.remove();
		
		assertEquals(6, six.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(7, seven.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(8, eight.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(9, nine.utpPacket().getSequenceNumber() & 0xFFFF);
		
		assertEquals(true, buffer.isEmpty());


	}
	
	
	/**
	 * Test for correct order on SEQ-Nr Overflow
	 * @throws IOException 
	 */
	@Test
	public void testOrderOnSeqNrOverflow() throws IOException {
		SkippedPacketBuffer buffer = new SkippedPacketBuffer();
		buffer.setExpectedSequenceNumber((int) (MAX_USHORT - 2));
		
		buffer.bufferPacket(createPacket(1));
		buffer.bufferPacket(createPacket((int) (MAX_USHORT - 1)));
		buffer.bufferPacket(createPacket(2));
		buffer.bufferPacket(createPacket((int) (MAX_USHORT)));
		
		Queue<UtpTimestampedPacketDTO> allPackets = buffer.getAllUntillNextMissing();

		UtpTimestampedPacketDTO minusOne = allPackets.remove();
		UtpTimestampedPacketDTO max = allPackets.remove();
		UtpTimestampedPacketDTO one = allPackets.remove();
		UtpTimestampedPacketDTO two = allPackets.remove();
		
		
		assertEquals((int) (MAX_USHORT - 1), minusOne.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals((int) MAX_USHORT, max.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(1, one.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(2, two.utpPacket().getSequenceNumber() & 0xFFFF);
		
		assertEquals(true, buffer.isEmpty());

		
	}
	
	/**
	 * Test correct order, missing max_unsigned_short
	 * @throws IOException 
	 */
	@Test
	public void testOrderOnSeqNrOverflowMaxMissing() throws IOException {
		SkippedPacketBuffer buffer = new SkippedPacketBuffer();
		buffer.setExpectedSequenceNumber((int) MAX_USHORT);
		
		buffer.bufferPacket(createPacket(1));
		buffer.bufferPacket(createPacket(4));
		buffer.bufferPacket(createPacket(3));
		buffer.bufferPacket(createPacket(2));

		
		Queue<UtpTimestampedPacketDTO> allPackets = buffer.getAllUntillNextMissing();

		UtpTimestampedPacketDTO one = allPackets.remove();
		UtpTimestampedPacketDTO two = allPackets.remove();
		UtpTimestampedPacketDTO three = allPackets.remove();
		UtpTimestampedPacketDTO four = allPackets.remove();
		
		
		assertEquals(1, one.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(2, two.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(3, three.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(4, four.utpPacket().getSequenceNumber() & 0xFFFF);
		
		assertEquals(true, buffer.isEmpty());

		
	}
	
	/**
	 * Test reindexing with 0 left
	 * @throws IOException 
	 */
	@Test
	public void testReindexing() throws IOException {
		SkippedPacketBuffer buffer = new SkippedPacketBuffer();
		buffer.setExpectedSequenceNumber(5);
		
		buffer.bufferPacket(createPacket(8));
		buffer.bufferPacket(createPacket(7));
		buffer.bufferPacket(createPacket(9));
		buffer.bufferPacket(createPacket(6));
		
		Queue<UtpTimestampedPacketDTO> allPackets = buffer.getAllUntillNextMissing();
		
		assertEquals(4, allPackets.size());
		UtpTimestampedPacketDTO six = allPackets.remove();
		UtpTimestampedPacketDTO seven = allPackets.remove();
		UtpTimestampedPacketDTO eight = allPackets.remove();
		UtpTimestampedPacketDTO nine = allPackets.remove();
		
		assertEquals(6, six.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(7, seven.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(8, eight.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(9, nine.utpPacket().getSequenceNumber() & 0xFFFF);

		buffer.reindex(9);
		
		Queue<UtpTimestampedPacketDTO> noPackets = buffer.getAllUntillNextMissing();

		assertEquals(0, noPackets.size());
		assertEquals(true, buffer.isEmpty());

		
	}
	
	/**
	 * Test reindexing with 0 left and overflow
	 * @throws IOException 
	 */
	
	@Test
	public void testReindexingOverflow() throws IOException {
		SkippedPacketBuffer buffer = new SkippedPacketBuffer();
		buffer.setExpectedSequenceNumber((int) (MAX_USHORT - 2));
		
		buffer.bufferPacket(createPacket(1));
		buffer.bufferPacket(createPacket((int) (MAX_USHORT - 1)));
		buffer.bufferPacket(createPacket(2));
		buffer.bufferPacket(createPacket((int) (MAX_USHORT)));
		
		Queue<UtpTimestampedPacketDTO> allPackets = buffer.getAllUntillNextMissing();
		assertEquals(4, allPackets.size());
		
		UtpTimestampedPacketDTO minusOne = allPackets.remove();
		UtpTimestampedPacketDTO max = allPackets.remove();
		UtpTimestampedPacketDTO one = allPackets.remove();
		UtpTimestampedPacketDTO two = allPackets.remove();
		
		assertEquals((int) (MAX_USHORT - 1), minusOne.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals((int) MAX_USHORT, max.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(1, one.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(2, two.utpPacket().getSequenceNumber() & 0xFFFF);
		
		buffer.reindex(2);
		Queue<UtpTimestampedPacketDTO> noPackets = buffer.getAllUntillNextMissing();
		assertEquals(0, noPackets.size());
		assertEquals(true, buffer.isEmpty());

	}
	

	/**
	 * Test reindexing when still missing 
	 * @throws IOException 
	 */
	
	@Test
	public void testReindexingStillMissing() throws IOException {
		SkippedPacketBuffer buffer = new SkippedPacketBuffer();
		buffer.setExpectedSequenceNumber(5);
		
		// 10 missing
		// 14 missing
		buffer.bufferPacket(createPacket(8));
		buffer.bufferPacket(createPacket(13));
		buffer.bufferPacket(createPacket(12));
		buffer.bufferPacket(createPacket(11));
		buffer.bufferPacket(createPacket(9));
		buffer.bufferPacket(createPacket(7));
		buffer.bufferPacket(createPacket(16));
		buffer.bufferPacket(createPacket(6));
		buffer.bufferPacket(createPacket(15));

		
		Queue<UtpTimestampedPacketDTO> allPackets = buffer.getAllUntillNextMissing();
		assertEquals(4, allPackets.size());
		
		UtpTimestampedPacketDTO six = allPackets.remove();
		UtpTimestampedPacketDTO seven = allPackets.remove();
		UtpTimestampedPacketDTO eight = allPackets.remove();
		UtpTimestampedPacketDTO nine = allPackets.remove();
		
		assertEquals(6, six.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(7, seven.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(8, eight.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(9, nine.utpPacket().getSequenceNumber() & 0xFFFF);

		buffer.reindex(9);
		Queue<UtpTimestampedPacketDTO> middlePackets = buffer.getAllUntillNextMissing();
		assertEquals(3, middlePackets.size());
		
		UtpTimestampedPacketDTO p11 = middlePackets.remove();
		UtpTimestampedPacketDTO p12 = middlePackets.remove();
		UtpTimestampedPacketDTO p13 = middlePackets.remove();
		
		assertEquals(11, p11.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(12, p12.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(13, p13.utpPacket().getSequenceNumber() & 0xFFFF);
		
		buffer.reindex(13);
		Queue<UtpTimestampedPacketDTO> lastPackets = buffer.getAllUntillNextMissing();
		assertEquals(2, lastPackets.size());
		
		UtpTimestampedPacketDTO p15 = lastPackets.remove();
		UtpTimestampedPacketDTO p16 = lastPackets.remove();
		
		assertEquals(15, p15.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(16, p16.utpPacket().getSequenceNumber() & 0xFFFF);

		buffer.reindex(16);
		Queue<UtpTimestampedPacketDTO> noPackets = buffer.getAllUntillNextMissing();
		assertEquals(0, noPackets.size());
		assertEquals(true, buffer.isEmpty());
		
	}
	
	
	/**
	 * Test reindexing when still missing and overflow occured
	 * @throws IOException 
	 */
	@Test
	public void testReindexingStillMissingOverflow() throws IOException {
		SkippedPacketBuffer buffer = new SkippedPacketBuffer();
		buffer.setExpectedSequenceNumber((int) (MAX_USHORT) - 2);
		
		//Missing 3, 7
		buffer.bufferPacket(createPacket(4));
		buffer.bufferPacket(createPacket(5));
		buffer.bufferPacket(createPacket(2));
		buffer.bufferPacket(createPacket((int) (MAX_USHORT) - 1));
		buffer.bufferPacket(createPacket(9));
		buffer.bufferPacket(createPacket(8));
		buffer.bufferPacket(createPacket((int) MAX_USHORT));
		buffer.bufferPacket(createPacket(6));
		buffer.bufferPacket(createPacket(1));
		
		Queue<UtpTimestampedPacketDTO> allPackets = buffer.getAllUntillNextMissing();
		assertEquals(4, allPackets.size());
		
		UtpTimestampedPacketDTO minusOne = allPackets.remove();
		UtpTimestampedPacketDTO max = allPackets.remove();
		UtpTimestampedPacketDTO one = allPackets.remove();
		UtpTimestampedPacketDTO two = allPackets.remove();
		
		assertEquals((int) (MAX_USHORT - 1), minusOne.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals((int) MAX_USHORT, max.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(1, one.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(2, two.utpPacket().getSequenceNumber() & 0xFFFF);

		buffer.reindex(2);
		Queue<UtpTimestampedPacketDTO> middlePackets = buffer.getAllUntillNextMissing();
		assertEquals(3, middlePackets.size());
		
		UtpTimestampedPacketDTO p4 = middlePackets.remove();
		UtpTimestampedPacketDTO p5 = middlePackets.remove();
		UtpTimestampedPacketDTO p6 = middlePackets.remove();
		
		assertEquals(4, p4.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(5, p5.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(6, p6.utpPacket().getSequenceNumber() & 0xFFFF);
		
		buffer.reindex(6);
		Queue<UtpTimestampedPacketDTO> lastPackets = buffer.getAllUntillNextMissing();
		assertEquals(2, lastPackets.size());
		
		UtpTimestampedPacketDTO p8 = lastPackets.remove();
		UtpTimestampedPacketDTO p9 = lastPackets.remove();
		
		assertEquals(8, p8.utpPacket().getSequenceNumber() & 0xFFFF);
		assertEquals(9, p9.utpPacket().getSequenceNumber() & 0xFFFF);

		buffer.reindex(9);
		Queue<UtpTimestampedPacketDTO> noPackets = buffer.getAllUntillNextMissing();
		assertEquals(0, noPackets.size());
		assertEquals(true, buffer.isEmpty());
	}
	
	@Test
	public void testBufferFull() throws IOException {
		SkippedPacketBuffer buffer = new SkippedPacketBuffer();
		buffer.setExpectedSequenceNumber(3); // add one packet
		assertEquals(999, buffer.getFreeSize());
		for (int i = 0; i < 998; i++) { // add another 998 -> totally 999 packets. i can have only now seq = 3;
			buffer.bufferPacket(createPacket(i + 4));
		}
		assertEquals(1, buffer.getFreeSize());
	}
	
	/**
	 * Test header extension with overflow and still missing packets
	 * @throws IOException 
	 */
	@Test
	public void testHeader() throws IOException {
		SkippedPacketBuffer buffer = new SkippedPacketBuffer();
		buffer.setExpectedSequenceNumber((int) (MAX_USHORT) - 2);	
		
		//Missing 3, 7
		buffer.bufferPacket(createPacket((int) (MAX_USHORT) - 1));
		buffer.bufferPacket(createPacket((int) MAX_USHORT));
		buffer.bufferPacket(createPacket(1));
		buffer.bufferPacket(createPacket(2));
		
		buffer.bufferPacket(createPacket(4));
		buffer.bufferPacket(createPacket(5));
		buffer.bufferPacket(createPacket(6));
		
		buffer.bufferPacket(createPacket(8));
		buffer.bufferPacket(createPacket(9));
		
		SelectiveAckHeaderExtension headerExtension = buffer.createHeaderExtension();
		
		/* first byte 11101111*/
		/* second byte 00000110*/
		assertEquals(longToUbyte(239), headerExtension.getBitMask()[0]); 
		assertEquals(longToUbyte(6), headerExtension.getBitMask()[1]);
		
		assertEquals(4, headerExtension.getBitMask().length);
		
		
		// [65534,65535, 1, 2, ..... 30] -> 30+2 = 32 -> length must be 4
		buffer.bufferPacket(createPacket(30));
		headerExtension = buffer.createHeaderExtension();
		assertEquals(4, headerExtension.getBitMask().length);
		
		
		// [65534,65535, 1, 2, ..... 31] -> 31+2 = 33 -> length must be 8 
		buffer.bufferPacket(createPacket(31));
		headerExtension = buffer.createHeaderExtension();
		assertEquals(8, headerExtension.getBitMask().length);
		
	
		// [65534,65535, 1, 2, ..... 62] -> 62+2 = 64 -> length must be 8 
		buffer.bufferPacket(createPacket(62));
		headerExtension = buffer.createHeaderExtension();
		assertEquals(8, headerExtension.getBitMask().length);

		
		// [65534,65535, 1, 2, ..... 63] -> 63+2 = 65 -> length must be 12 
		buffer.bufferPacket(createPacket(63));
		headerExtension = buffer.createHeaderExtension();
		assertEquals(12, headerExtension.getBitMask().length);
	}
	

}
