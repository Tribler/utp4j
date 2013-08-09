package ch.uzh.csg.utp4j.channels.impl;

import java.net.DatagramPacket;

import ch.uzh.csg.utp4j.data.UtpPacket;



public class UtpTimestampedPacketDTO {

	private DatagramPacket packet;
	private Long timestamp;
	private UtpPacket utpPacket;
	private int utpTimeStamp;
	private int ackedAfterMeCounter = 0;
	
	private boolean isPacketAcked = false;

	private long ackTimeStamp = 0;
	private boolean markedToResend;
	
	public UtpTimestampedPacketDTO(DatagramPacket p, UtpPacket u, Long s, int utpStamp) {
		this.timestamp = s;
		this.utpPacket = u;
		this.packet = p;
		this.utpTimeStamp = utpStamp;
	}

	public DatagramPacket dataGram() {
		return packet;
	}
	
	public Long stamp() {
		return timestamp;
	}
	
	public UtpPacket utpPacket() {
		return utpPacket;
	}
	
	public int utpTimeStamp() {
		return utpTimeStamp;
	}

	public boolean isPacketAcked() {
		return isPacketAcked;
	}
	
	public void setPacketAcked(boolean isPacketAcked) {
		this.isPacketAcked = isPacketAcked;
	}
	
	public long getAckTimeStamp() {
		return ackTimeStamp;
	}
	
	public void setAckTimeStamp(long ackTimeStamp) {
		this.ackTimeStamp = ackTimeStamp;
	}

	public int getAckedAfterMeCounter() {
		return ackedAfterMeCounter;
	}

	public void setAckedAfterMeCounter(int ackedAfterMeCounter) {
		this.ackedAfterMeCounter = ackedAfterMeCounter;
	}
	
	public void incrementAckedAfterMe() {
		ackedAfterMeCounter++;
	}

	public boolean alreadyMarkedToResend() {
		return markedToResend;
	}
	
	public void markToResend(boolean val) {
		markedToResend = val;
	}
}
