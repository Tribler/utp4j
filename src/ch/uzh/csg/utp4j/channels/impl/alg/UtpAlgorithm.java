package ch.uzh.csg.utp4j.channels.impl.alg;

import java.net.DatagramPacket;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import ch.uzh.csg.utp4j.channels.impl.UdpPacketTimeStampPair;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

public class UtpAlgorithm {
	
	private AtomicInteger currentWindowSize = new AtomicInteger(3);
	private AtomicInteger packetsOnTheFly = new AtomicInteger(0);

	public void ackRecieved(UdpPacketTimeStampPair pair) {
		packetsOnTheFly.decrementAndGet();
	
		System.out.println("Ack recieved: " + (UtpPacketUtils.extractUtpPacket(pair.packet()).getAckNumber() & 0xFFFF));
	}
		
	public Queue<DatagramPacket> getPacketsToResend() {
		return new LinkedList<DatagramPacket>();
	}
	
	public boolean canSendNextPacket() {
		System.out.println("Can Send Next package? : " + (packetsOnTheFly.get() < currentWindowSize.get()));
		return packetsOnTheFly.get() < currentWindowSize.get();
	}
	
	public int sizeOfNextPacket() {
		return 10;
	}

	public void packetIsOnTheFly(DatagramPacket packet) {
		packetsOnTheFly.incrementAndGet();
//		System.out.println("Sended, now packets onfly: " + packetsOnTheFly.get());

	}
}
