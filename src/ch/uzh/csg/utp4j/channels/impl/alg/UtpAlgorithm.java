package ch.uzh.csg.utp4j.channels.impl.alg;

import java.net.DatagramPacket;
import java.util.LinkedList;
import java.util.Queue;

import ch.uzh.csg.utp4j.channels.impl.UdpPacketTimeStampPair;

public class UtpAlgorithm {

	public void ackRecieved(UdpPacketTimeStampPair pair) {
		
	}
		
	public Queue<DatagramPacket> getPacketsToResend() {
		return new LinkedList<DatagramPacket>();
	}
	
	public boolean canSendNextPacket() {
		return true;
	}
	
	public int sizeOfNextPacket() {
		return 10;
	}

	public void packedSend(DatagramPacket packet) {
		
	}
}
