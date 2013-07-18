package ch.uzh.csg.utp4j.channels.impl.read;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;

import ch.uzh.csg.utp4j.channels.impl.UdpPacketTimeStampPair;
import ch.uzh.csg.utp4j.channels.impl.UtpSocketChannelImpl;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

public class UtpReadingRunnable extends Thread implements Runnable {
	
	private IOException exp;
	private ByteBuffer buffer;
	private UtpSocketChannelImpl channel;
	private boolean exceptionOccured = false;
	private boolean graceFullInterrupt;
	private boolean isRunning;
	
	public UtpReadingRunnable(UtpSocketChannelImpl channel, ByteBuffer buff) {
		this.channel = channel;
		this.buffer = buff;
	}
	
	public int getBytesRead() {
		return 0;
	}

	public IOException getException() {
		return exp;
	}

	public boolean hasExceptionOccured() {
		return false;
	}

	@Override
	public void run() {
		buffer.flip();
		isRunning = true;
		while(continueReading()) {
			Queue<UdpPacketTimeStampPair> queue = channel.getDataGramQueue();
			while(!queue.isEmpty()) {
				try {
					DatagramPacket packet = queue.poll().packet();
					UtpPacket utpPacket = UtpPacketUtils.extractUtpPacket(packet);
					channel.ackPacket(utpPacket);
					byte[] payload = utpPacket.getPayload();
//					System.out.println("Read Seq: " + (utpPacket.getSequenceNumber() & 0xFFFF) + " " + Arrays.toString(payload));
//					buffer.put(payload);
				} catch (IOException exp) {
					exceptionOccured = true;
					this.exp = exp;
					break;
				}
			}
		}
		isRunning = false;
		// has queue?
		//	ack
		//  put in byteBuffer
	}
	
	public void graceFullInterrupt() {
		this.graceFullInterrupt = true;
	}

	private boolean continueReading() {
		return !graceFullInterrupt && !exceptionOccured;
	}
	
	public boolean isRunning() {
		return isRunning;
	}

}
