package ch.uzh.csg.utp4j.channels.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketState;
import ch.uzh.csg.utp4j.channels.impl.read.UtpReadingRunnable;
import ch.uzh.csg.utp4j.channels.impl.recieve.UtpPacketRecievable;
import ch.uzh.csg.utp4j.channels.impl.recieve.UtpRecieveRunnable;
import ch.uzh.csg.utp4j.channels.impl.write.UtpWritingRunnable;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

import static ch.uzh.csg.utp4j.channels.UtpSocketState.CLOSED;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.CONNECTED;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.SYN_ACKING_FAILED;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUint;



public class UtpSocketChannelImpl extends UtpSocketChannel implements UtpPacketRecievable {
	
	private volatile Queue<UdpPacketTimeStampPair> queue = new ConcurrentLinkedQueue<UdpPacketTimeStampPair>();
	
	private UtpRecieveRunnable reciever;
	private UtpWritingRunnable writer;
	private UtpReadingRunnable reader;
	
	
	@Override
	public void recievePacket(DatagramPacket udpPacket) {
		if (UtpPacketUtils.isSynPkt(udpPacket)) {
			handleIncommingConnectionRequest(udpPacket);
		} else if (getState() == UtpSocketState.SYN_SENT) {
			handleSynSendAck(udpPacket);
		} else if (acceptDataPackets()) { //TODO: HERE TRUE REMOVAL
				handlePacket(udpPacket);
				
		}
	}
	
	private boolean acceptDataPackets() {
		boolean accept =  CLOSED != getState() && ( 
			(writer != null ? writer.isRunning() : false)
		 || (reader != null ? reader.isRunning() : false)); 
		return accept || true;
	}
	
	private void handleSynSendAck(DatagramPacket udpPacket) {
		UtpPacket pkt = UtpPacketUtils.extractUtpPacket(udpPacket);
		setAckNrFromPacketSqNr(pkt);
		setState(CONNECTED);
		printState("[SynAck recieved] ");
	}

	private void handlePacket(DatagramPacket udpPacket) {
		
		queue.add(new UdpPacketTimeStampPair(udpPacket, timeStamper.timeStamp()));

	}

	public void setupRandomSeqNumber() {
		Random rnd = new Random();
		int max = (int) (MAX_USHORT - 1);
		int rndInt = rnd.nextInt(max);
		setSequenceNumber(rndInt);
		
	}
	
	private void handleIncommingConnectionRequest(DatagramPacket udpPacket) {
		if (getState() != CLOSED) {
			ChannelStateException exp = new ChannelStateException("Recieved Syn Packet but Channel is already connected");
			exp.setState(getState());
			exp.setAddresse(udpPacket.getSocketAddress());
			throw exp;
		}
		
		UtpPacket utpPacket = UtpPacketUtils.extractUtpPacket(udpPacket);
		setRemoteAddress(udpPacket.getSocketAddress());
		setConnectionIdsFromPacket(utpPacket);
			
		setupRandomSeqNumber();
		setAckNrFromPacketSqNr(utpPacket);
		printState("[Syn recieved] ");
			
		UtpPacket ackPacket = createAckPacket(utpPacket);
		try {
			sendPacket(ackPacket);			
			setState(CONNECTED);
 		} catch (IOException exp) {
 			setRemoteAddress(null);
 			setConnectionIdsending((short) 0);
 			setConnectionIdRecieving((short) 0);
 			setAckNumber(0);
 			setState(SYN_ACKING_FAILED);
 			System.out.println("ACKING FAILED");
 		}
		
	}

	private void setAckNrFromPacketSqNr(UtpPacket utpPacket) {
		short ackNumberS = utpPacket.getSequenceNumber();
		setAckNumber(ackNumberS & 0xFFFF);
	}

	private void setConnectionIdsFromPacket(UtpPacket utpPacket) {
		short connId = utpPacket.getConnectionId();
		int connIdSender = (connId & 0xFFFF);
		int connIdRec = (connId & 0xFFFF) + 1;
		setConnectionIdsending(connIdSender);
		setConnectionIdRecieving(connIdRec);
		
	}

	@Override
	protected void connectImpl() {
		reciever = new UtpRecieveRunnable(getDgSocket(), this);
		reciever.start();
		
	}

	@Override
	protected void abortImpl() {
		if (reciever != null) {
			reciever.interrupt();
		}
		
	}

	@Override
	protected int writeImpl(ByteBuffer src) throws IOException {
		writer = new UtpWritingRunnable(this, src);
		if (!isBlocking) {
			writer.start();			
		} else {
			writer.run();
		}
		if (writer.hasExceptionOccured()) {
			throw writer.getException();
		}
		return writer.getBytesSend();
	}

	public Queue<UdpPacketTimeStampPair> getDataGramQueue() {
		return queue;
	}
	
	public UtpPacket getNextDataPacket() {
		return createDataPacket();
	}

	@Override
	protected int readImpl(ByteBuffer dst) throws IOException {
		reader = new UtpReadingRunnable(this, dst);
		if (!isBlocking) {
			reader.start();
		} else {
			reader.run();
		}
		if (reader.hasExceptionOccured()) {
			throw reader.getException();
		}
		return reader.getBytesRead();
	}

	public void ackPacket(UtpPacket utpPacket) throws IOException {
				UtpPacket ackPacket = createAckPacket(utpPacket);
				sendPacket(ackPacket);				
	}

}
