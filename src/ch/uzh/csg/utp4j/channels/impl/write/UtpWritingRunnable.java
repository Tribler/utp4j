package ch.uzh.csg.utp4j.channels.impl.write;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;


import ch.uzh.csg.utp4j.channels.impl.UdpPacketTimeStampPair;
import ch.uzh.csg.utp4j.channels.impl.UtpSocketChannelImpl;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgorithm;
import ch.uzh.csg.utp4j.data.UtpPacket;

public class UtpWritingRunnable extends Thread implements Runnable {
	
	private ByteBuffer buffer;
	volatile boolean graceFullInterrupt;
	private UtpSocketChannelImpl channel;
	private boolean isRunning = false;
	private UtpAlgorithm algorithm = new UtpAlgorithm();
	private IOException possibleException = null;
	
	public UtpWritingRunnable(UtpSocketChannelImpl channel, ByteBuffer buffer) {
		this.buffer = buffer;
		this.channel = channel;
	}


	@Override
	public void run() {
		isRunning = true;
		IOException possibleExp = null;
		boolean exceptionOccured = false;
		buffer.flip();
		
		while(continueSending()) {
			Queue<UdpPacketTimeStampPair> queue = channel.getDataGramQueue();
			while(!queue.isEmpty()) {
				UdpPacketTimeStampPair pair = queue.poll();
				algorithm.ackRecieved(pair);
			}

			Queue<DatagramPacket> packetsToResend = algorithm.getPacketsToResend();
			for (DatagramPacket datagramPacket : packetsToResend) {
				try {
					channel.getDgSocket().send(datagramPacket);					
				} catch (IOException exp) {
					graceFullInterrupt = true;
					possibleExp = exp;
					exceptionOccured = true;
					break;
				}
			}
			
			while (algorithm.canSendNextPacket() && !exceptionOccured && continueSending()) {
				int packetSize = algorithm.sizeOfNextPacket();
				try {
					if (buffer.remaining() < packetSize) {
						packetSize = buffer.remaining();
					}
					byte[] payload = new byte[packetSize];
					buffer.get(payload);
					UtpPacket utpPacket = channel.getNextDataPacket();
					utpPacket.setPayload(payload);
					System.out.println("Sending Seq: " + (utpPacket.getSequenceNumber() & 0xFFFF) +  "  " + Arrays.toString(payload));
					byte[] utpPacketBytes = utpPacket.toByteArray();
					DatagramPacket packet = new DatagramPacket(utpPacketBytes, utpPacketBytes.length, channel.getRemoteAdress());
					algorithm.packetIsOnTheFly(packet);
					channel.getDgSocket().send(packet);					
				} catch (IOException exp) {
					graceFullInterrupt = true;
					possibleExp = exp;
					exceptionOccured = true;
					break;
				}
			}
		}
		
		if (possibleExp != null) {
			exceptionOccured(possibleExp);
		}
		
		isRunning = false;
	}
	
	private void exceptionOccured(IOException exp) {
		possibleException = exp;
	}
	
	public boolean hasExceptionOccured() {
		return possibleException != null;
	}
	
	public IOException getException() {
		return possibleException;
	}
	
	private boolean continueSending() {
		return !graceFullInterrupt && buffer.hasRemaining();
	}
	
	public void graceFullInterrupt() {
		graceFullInterrupt = true;
	}

	public int getBytesSend() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public boolean isRunning() {
		return isRunning;
	}
}
