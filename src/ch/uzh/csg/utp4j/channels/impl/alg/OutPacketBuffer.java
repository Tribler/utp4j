package ch.uzh.csg.utp4j.channels.impl.alg;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

public class OutPacketBuffer {
	
	private static final int MAX_SKIP_PACKET_BEFORE_RESEND = 3;
	private ArrayList<UtpTimestampedPacketDTO> buffer = new ArrayList<UtpTimestampedPacketDTO>();
	private int bytesOnFly = 0;
	

	public void bufferPacket(UtpTimestampedPacketDTO pkt) {
		buffer.add(pkt);
		if (pkt.utpPacket().getPayload() != null) {
			bytesOnFly += pkt.utpPacket().getPayload().length;
		}
		bytesOnFly += UtpPacketUtils.DEF_HEADER_LENGTH;
	}

	public boolean isEmpty() {
		return buffer.isEmpty();
	}

	public boolean markPacketAcked(int seqNrToAck, long timestamp) {
		boolean returnValue = false;
		for (UtpTimestampedPacketDTO pkt : buffer) {
			if ((pkt.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
				if (!pkt.isPacketAcked()) {
					pkt.setAckTimeStamp(timestamp);
					pkt.setPacketAcked(true);
					System.out.println("ACK angekommen seqNr: " + seqNrToAck);
					returnValue = true;
					break;
				}
			}
		}
		removeAcked();
		return returnValue;
	}

	private void removeAcked() {
		ArrayList<UtpTimestampedPacketDTO> toRemove = new ArrayList<UtpTimestampedPacketDTO>();
		for (UtpTimestampedPacketDTO pkt : buffer) {
			if (pkt.isPacketAcked()) {
				toRemove.add(pkt);
				if (pkt.utpPacket().getPayload() != null) {
					bytesOnFly -= pkt.utpPacket().getPayload().length;
				}
				bytesOnFly -= UtpPacketUtils.DEF_HEADER_LENGTH;
			} 
		}
		buffer.removeAll(toRemove);
	}

	public Queue<UtpTimestampedPacketDTO> getPacketsToResend() {
		Queue<UtpTimestampedPacketDTO> queue = new LinkedList<UtpTimestampedPacketDTO>();
		
		UtpTimestampedPacketDTO firstUnacked = null;
		for (UtpTimestampedPacketDTO pkt : buffer) {
			if (!pkt.isPacketAcked()) {
				firstUnacked = pkt;
				break;
			} 	
		}
		if (firstUnacked != null) {
			UtpTimestampedPacketDTO x1 = firstUnacked;
			int totalRange = 0;
			for (UtpTimestampedPacketDTO pkt : buffer) {
				int range = 0;
				if (pkt != x1) {

					range = (pkt.utpPacket().getSequenceNumber() & 0xFFFF) 
							- (x1.utpPacket().getSequenceNumber() & 0xFFFF);
					if (range > 1) {
						totalRange = range -1;
					}
					x1 = pkt;
					
				}
				if (totalRange > MAX_SKIP_PACKET_BEFORE_RESEND) {
					System.out.println("toResend: " + (firstUnacked.utpPacket().getSequenceNumber() & 0xFFFF));
					queue.add(firstUnacked);
					break;
				}
			}
		}
		return queue;
	}

	public int getBytesOnfly() {
		return bytesOnFly;
	}

}
