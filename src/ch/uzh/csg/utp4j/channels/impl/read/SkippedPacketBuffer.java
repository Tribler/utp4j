package ch.uzh.csg.utp4j.channels.impl.read;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUbyte;

import java.util.LinkedList;
import java.util.Queue;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.data.SelectiveAckHeaderExtension;

public class SkippedPacketBuffer {

	private int size = 1000;
	private UtpTimestampedPacketDTO[] buffer = new UtpTimestampedPacketDTO[size];
	private int expectedSequenceNumber = 0;
	private int elementCount = 0;
	
		
	public void bufferPacket(UtpTimestampedPacketDTO pkt) {
		int sequenceNumber = pkt.utpPacket().getSequenceNumber() & 0xFFFF;
		int position = sequenceNumber - expectedSequenceNumber;
		if (position < 0) {
			position = mapOverflowPosition(sequenceNumber);
		}
		elementCount++;
		buffer[position] = pkt;

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
		for (int i = 1; i < size; i++) {
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
		float quotient = (float) (size/32.00);
		int length = (int) Math.ceil(quotient);
		return length*4;
		
	}

	private int getRange() {
		int range = 0;
		for (int i = 0; i < size; i++) {
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
		for (int i = 1; i < size; i++) {
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

	public void reindex(int lastSeqNumber) {
		int expectedSequenceNumber = 0;
		if (lastSeqNumber == MAX_USHORT) {
			expectedSequenceNumber = 1;
		} else {
			expectedSequenceNumber = lastSeqNumber + 1;			
		}
		setExpectedSequenceNumber(expectedSequenceNumber);
		UtpTimestampedPacketDTO[] oldBuffer = buffer;
		buffer = new UtpTimestampedPacketDTO[size];
		elementCount = 0;
		for (UtpTimestampedPacketDTO utpTimestampedPacket : oldBuffer) {
			if (utpTimestampedPacket != null) {
				bufferPacket(utpTimestampedPacket);
			}
		}
		
		
		
	}
	

}
