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
	private boolean reduceWindow;
	
	boolean resendBecauseSkipped;
	
	public UtpTimestampedPacketDTO(DatagramPacket p, UtpPacket u, Long s, int utpStamp) {
		this.timestamp = s;
		this.utpPacket = u;
		this.packet = p;
		this.utpTimeStamp = utpStamp;
	}

	public DatagramPacket dataGram() {
		return packet;
	}
	
	public void setDgPacket(DatagramPacket p) {
		this.packet = p;
	}
	
	public Long stamp() {
		return timestamp;
	}
	
	public void setStamp(long stamp) {
		this.timestamp = stamp;
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
	
	public int getAckedAfterMeCounter() {
		return ackedAfterMeCounter;
	}

	public void setAckedAfterMeCounter(int ackedAfterMeCounter) {
		this.ackedAfterMeCounter = ackedAfterMeCounter;
	}
	
	public void incrementAckedAfterMe() {
		ackedAfterMeCounter++;
	}

	public boolean reduceWindow() {
		return reduceWindow;
	}
	
	public void setReduceWindow(boolean val) {
		reduceWindow = val;
	}

	public boolean alreadyResendBecauseSkipped() {
		return resendBecauseSkipped;
	}
	
	public void setResendBecauseSkipped(boolean value) {
		resendBecauseSkipped = value;
	}

}
