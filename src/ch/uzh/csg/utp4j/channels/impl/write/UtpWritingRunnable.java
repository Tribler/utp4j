package ch.uzh.csg.utp4j.channels.impl.write;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import ch.uzh.csg.utp4j.channels.impl.UtpSocketChannelImpl;
import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgorithm;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.UtpPacket;

public class UtpWritingRunnable extends Thread implements Runnable {
	
	private ByteBuffer buffer;
	volatile boolean graceFullInterrupt;
	private UtpSocketChannelImpl channel;
	private boolean isRunning = false;
	private UtpAlgorithm algorithm;
	IOException possibleException = null;
	private boolean finSend;
	private MicroSecondsTimeStamp timeStamper;
	
	public UtpWritingRunnable(UtpSocketChannelImpl channel, ByteBuffer buffer, MicroSecondsTimeStamp timeStamper) {
		this.buffer = buffer;
		this.channel = channel;
		this.timeStamper = timeStamper;
		algorithm = new UtpAlgorithm(timeStamper);
	}


	@Override
	public void run() {
		algorithm.initiateAckPosition(channel.getSequenceNumber());
		algorithm.setTimeStamper(timeStamper);
		isRunning = true;
		IOException possibleExp = null;
		boolean exceptionOccured = false;
		buffer.flip();
		int durchgang = 0;
		while(continueSending()) {
			try {
				checkForAcks();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Queue<DatagramPacket> packetsToResend = algorithm.getPacketsToResend();
			for (DatagramPacket datagramPacket : packetsToResend) {
				try {
					datagramPacket.setSocketAddress(channel.getRemoteAdress());
					channel.sendPacket(datagramPacket);	
				} catch (IOException exp) {
					exp.printStackTrace();
					graceFullInterrupt = true;
					possibleExp = exp;
					exceptionOccured = true;
					break;
				}
			}
			while (algorithm.canSendNextPacket() && !exceptionOccured && !graceFullInterrupt && buffer.hasRemaining()) {
				int packetSize = algorithm.sizeOfNextPacket();
				try {
					sendNextPacket(packetSize);
				} catch (IOException exp) {
					exp.printStackTrace();
					graceFullInterrupt = true;
					possibleExp = exp;
					exceptionOccured = true;
					break;
				}
			}
			if (!buffer.hasRemaining() && !finSend) {
				UtpPacket fin = channel.getFinPacket();
				System.out.println("Sending FIN");
				try {
					channel.finalizeConnection(fin);
					algorithm.markFinOnfly(fin);
				} catch (IOException exp) {
					exp.printStackTrace();
					graceFullInterrupt = true;
					possibleExp = exp;
					exceptionOccured = true;
				}
				finSend = true;
			}
			durchgang++;
			if (durchgang % 1000 == 0) {
				System.out.println("buffer position: " + buffer.position() + " buffer limit: " + buffer.limit());
			}
		}

		if (possibleExp != null) {
			exceptionOccured(possibleExp);
		}
		isRunning = false;
		System.out.println("WRITER OUT");
	}
	
	private void checkForAcks() throws InterruptedException {
		BlockingQueue<UtpTimestampedPacketDTO> queue = channel.getDataGramQueue();
		if (queue.peek() == null) {
			processAcks(queue);
		} else {
			waitAndProcessAcks(queue);
		}
	}
	
	private void waitAndProcessAcks(BlockingQueue<UtpTimestampedPacketDTO> queue) throws InterruptedException {
		BlockingQueue<UtpTimestampedPacketDTO> tempQueue = new LinkedBlockingQueue<UtpTimestampedPacketDTO>();
		long timeOut = algorithm.getMsToNextTimeOut();
		UtpTimestampedPacketDTO temp = queue.poll(timeOut, TimeUnit.MILLISECONDS);
		if (temp != null) {
			tempQueue.offer(temp);
			while(queue.peek() != null) {
				tempQueue.offer(queue.take());
			}
			processAcks(tempQueue);
		}
	}

	private void processAcks(BlockingQueue<UtpTimestampedPacketDTO> queue) throws InterruptedException {
		while(queue.peek() != null) {
			UtpTimestampedPacketDTO pair = queue.take();
			algorithm.ackRecieved(pair);
		}
		algorithm.removeAcked();		
	}

	private void sendNextPacket(int packetSize) throws IOException {
		if (buffer.remaining() < packetSize) {
			packetSize = buffer.remaining();
		}
		byte[] payload = new byte[packetSize];
		buffer.get(payload);
		UtpPacket utpPacket = channel.getNextDataPacket();
		utpPacket.setPayload(payload);
		byte[] utpPacketBytes = utpPacket.toByteArray();
		DatagramPacket udpPacket = new DatagramPacket(utpPacketBytes, utpPacketBytes.length, channel.getRemoteAdress());
		algorithm.markPacketOnfly(utpPacket, udpPacket);
		channel.sendPacket(udpPacket);		
		
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
		return !graceFullInterrupt && !allPacketsAckedSendAndAcked();
	}
	
	private boolean allPacketsAckedSendAndAcked() {

		return finSend && algorithm.areAllPacketsAcked() && !buffer.hasRemaining();
	}


	public void graceFullInterrupt() {
		graceFullInterrupt = true;
	}

	public int getBytesSend() {
		return 0;
	}
	
	public boolean isRunning() {
		return isRunning;
	}
}
