package ch.uzh.csg.utp4j.channels.impl;

import java.net.DatagramPacket;



public class UdpPacketTimeStampPair {

	private DatagramPacket packet;
	private Long timestamp;
	
	public UdpPacketTimeStampPair(DatagramPacket p, Long s) {
		this.timestamp = s;
		this.packet = p;
	}

	public DatagramPacket packet() {
		return packet;
	}
	
	public Long stamp() {
		return timestamp;
	}
	
	
	
}
