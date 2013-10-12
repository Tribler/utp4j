package ch.uzh.csg.utp4j.channels.impl.read;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.acl.LastOwnerException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgorithm;
import ch.uzh.csg.utp4j.data.SelectiveAckHeaderExtension;

public class SkippedPacketBuffer {

	private static final int SIZE = 2000;
	private UtpTimestampedPacketDTO[] buffer = new UtpTimestampedPacketDTO[SIZE];
	private int expectedSequenceNumber = 0;
	private int elementCount = 0;
	private int debug_lastSeqNumber;
	private int debug_lastPosition;
	
	private static final Logger log = LoggerFactory.getLogger(SkippedPacketBuffer.class);

	public void bufferPacket(UtpTimestampedPacketDTO pkt) throws IOException {
		int sequenceNumber = pkt.utpPacket().getSequenceNumber() & 0xFFFF;
		int position = sequenceNumber - expectedSequenceNumber;
		debug_lastSeqNumber = sequenceNumber;
		if (position < 0) {
			position = mapOverflowPosition(sequenceNumber);
		}
		debug_lastPosition = position;
		elementCount++;
		try {
			buffer[position] = pkt;			
		} catch (ArrayIndexOutOfBoundsException ioobe) {
			log.error("seq, exp: " + sequenceNumber + " " + expectedSequenceNumber + " ");
			ioobe.printStackTrace();

			dumpBuffer("oob: " + ioobe.getMessage());
			throw new IOException();
		}

	}
	
	private int mapOverflowPosition(int sequenceNumber) {
		return (int) (MAX_USHORT - expectedSequenceNumber + sequenceNumber);
	}

	public void setExpectedSequenceNumber(int seq) { 
		this.expectedSequenceNumber = seq;
	}
	
	public int getExpectedSequenceNumber() {
		return expectedSequenceNumber;
	}

	public SelectiveAckHeaderExtension createHeaderExtension() {
		SelectiveAckHeaderExtension header = new SelectiveAckHeaderExtension();
		int length = calculateHeaderLength();
		byte[] bitMask = new byte[length];
		fillBitMask(bitMask);
		header.setBitMask(bitMask);
		
		return header;
	}

	private void fillBitMask(byte[] bitMask) {
		int bitMaskIndex = 0;
		for (int i = 1; i < SIZE; i++) {
			int bitMapIndex = (i - 1) % 8;
			boolean hasRecieved = buffer[i] != null;
			
			if (hasRecieved) {
				int bitPattern = (SelectiveAckHeaderExtension.BITMAP[bitMapIndex] & 0xFF) & 0xFF;
				bitMask[bitMaskIndex] = (byte) ((bitMask[bitMaskIndex] & 0xFF) | bitPattern);
			}
			
			if (i % 8 == 0) {
				bitMaskIndex++;
			}
		}
	}

	
	private int calculateHeaderLength() {
		int size = getRange();
		return (((size - 1) / 32 ) + 1) * 4;
	}

	private int getRange() {
		int range = 0;
		for (int i = 0; i < SIZE; i++) {
			if (buffer[i] != null) {
				range = i;
			}
		}
		return range;
	}


	public boolean isEmpty() {
		return elementCount == 0;
	}

	public Queue<UtpTimestampedPacketDTO> getAllUntillNextMissing() {
		Queue<UtpTimestampedPacketDTO> queue = new LinkedList<UtpTimestampedPacketDTO>();
		for (int i = 1; i < SIZE; i++) {
			if (buffer[i] != null) {
				queue.add(buffer[i]);
				buffer[i] = null;
			} else {
				break;
			}
		}
		elementCount -= queue.size();
		return queue;
	} 

	public void reindex(int lastSeqNumber) throws IOException {
		int expectedSequenceNumber = 0;
		if (lastSeqNumber == MAX_USHORT) {
			expectedSequenceNumber = 1;
		} else {
			expectedSequenceNumber = lastSeqNumber + 1;			
		}
		setExpectedSequenceNumber(expectedSequenceNumber);
		UtpTimestampedPacketDTO[] oldBuffer = buffer;
		buffer = new UtpTimestampedPacketDTO[SIZE];
		elementCount = 0;
		for (UtpTimestampedPacketDTO utpTimestampedPacket : oldBuffer) {
			if (utpTimestampedPacket != null) {
				bufferPacket(utpTimestampedPacket);
			}
		}
		
		
		
	}

	public int getFreeSize() throws IOException {
		if (SIZE - elementCount < 0) {
			dumpBuffer("freesize negative");
		}
		if (SIZE - elementCount < 50) {
			return 0;
		}
		return SIZE-elementCount-1;
	}

	private void dumpBuffer(String string) throws IOException {
		if (UtpAlgConfiguration.DEBUG) {
			
			log.debug("dumping buffer");
			RandomAccessFile aFile = new RandomAccessFile("testData/auto/bufferdump.txt", "rw");
			FileChannel inChannel = aFile.getChannel();
			inChannel.truncate(0);
			ByteBuffer bbuffer = ByteBuffer.allocate(100000);
			bbuffer.put((new SimpleDateFormat("dd_MM_hh_mm_ss")).format(new Date()).getBytes());
			bbuffer.put((string + "\n").getBytes());
			bbuffer.put(("SIZE: " + 	Integer.toString(SIZE) + "\n").getBytes());
			bbuffer.put(("count: " + 	Integer.toString(elementCount) + "\n").getBytes());
			bbuffer.put(("expect: " + 	Integer.toString(expectedSequenceNumber) + "\n").getBytes());
			bbuffer.put(("lastSeq: " + 	Integer.toString(debug_lastSeqNumber) + "\n").getBytes());
			bbuffer.put(("lastPos: " + 	Integer.toString(debug_lastPosition) + "\n").getBytes());
	
			for (int i = 0; i < SIZE; i++) {
				String seq;
				if (buffer[i] == null) {
					seq = "_; ";
				} else {
					seq = Integer.toString((buffer[i].utpPacket().getSequenceNumber() & 0xFFFF)) + "; ";
				}
				bbuffer.put((Integer.toString(i) + " -> " + seq).getBytes());
				if (i % 50 == 0) {
					bbuffer.put("\n".getBytes());
				}
			}
			log.debug(bbuffer.position() + " " + bbuffer.limit());
			bbuffer.flip();
			while(bbuffer.hasRemaining()) {
				inChannel.write(bbuffer);
			}
			aFile.close();
			inChannel.close();
		}
		
	}
	

}
