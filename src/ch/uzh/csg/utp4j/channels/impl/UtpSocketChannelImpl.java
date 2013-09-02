package ch.uzh.csg.utp4j.channels.impl;

import static ch.uzh.csg.utp4j.channels.UtpSocketState.CLOSED;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.CONNECTED;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.SYN_ACKING_FAILED;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUint;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketState;
import ch.uzh.csg.utp4j.channels.UtpWriteFuture;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgorithm;
import ch.uzh.csg.utp4j.channels.impl.read.UtpReadingRunnable;
import ch.uzh.csg.utp4j.channels.impl.recieve.UtpPacketRecievable;
import ch.uzh.csg.utp4j.channels.impl.recieve.UtpRecieveRunnable;
import ch.uzh.csg.utp4j.channels.impl.write.UtpWriteFutureImpl;
import ch.uzh.csg.utp4j.channels.impl.write.UtpWritingRunnable;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.SelectiveAckHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;



public class UtpSocketChannelImpl extends UtpSocketChannel implements UtpPacketRecievable {
	
	private volatile BlockingQueue<UtpTimestampedPacketDTO> queue = new LinkedBlockingQueue<UtpTimestampedPacketDTO>();
	
	private UtpRecieveRunnable reciever;
	private UtpWritingRunnable writer;
	private UtpReadingRunnable reader;
	private Object sendLock = new Object();
	
	
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
		UtpPacket utpPacket = UtpPacketUtils.extractUtpPacket(udpPacket);
		queue.offer(new UtpTimestampedPacketDTO(udpPacket, utpPacket, timeStamper.timeStamp(), timeStamper.utpTimeStamp()));

	}


	public void ackPacket(UtpPacket utpPacket, int timestampDifference, long windowSize) throws IOException {
		UtpPacket ackPacket = createAckPacket(utpPacket, timestampDifference, windowSize);
		sendPacket(ackPacket);		
	}


	public void setupRandomSeqNumber() {
		Random rnd = new Random();
		int max = (int) (MAX_USHORT - 1);
		int rndInt = rnd.nextInt(max);
		setSequenceNumber(rndInt);
		
	}
	
	private void handleIncommingConnectionRequest(DatagramPacket udpPacket) {
		int timeStamp = timeStamper.utpTimeStamp();
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
		int timestampDifference = timeStamper.utpDifference(timeStamp, utpPacket.getTimestamp());
		UtpPacket ackPacket = createAckPacket(utpPacket, timestampDifference, UtpAlgorithm.MAX_PACKET_SIZE*1000);
		try {
			sendPacket(ackPacket);			
			setState(CONNECTED);
 		} catch (IOException exp) {
 			setRemoteAddress(null);
 			setConnectionIdsending((short) 0);
 			setConnectionIdRecieving((short) 0);
 			setAckNumber(0);
 			setState(SYN_ACKING_FAILED);
 		}
		
	}

	protected void setAckNrFromPacketSqNr(UtpPacket utpPacket) {
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
	protected UtpWriteFuture writeImpl(ByteBuffer src) throws IOException {
		UtpWriteFutureImpl future = new UtpWriteFutureImpl();
		writer = new UtpWritingRunnable(this, src, timeStamper, future);
		writer.start();			
		if (writer.hasExceptionOccured()) {
			throw writer.getException();
		}
		return future;
	}

	public BlockingQueue<UtpTimestampedPacketDTO> getDataGramQueue() {
		return queue;
	}
	
	public UtpPacket getNextDataPacket() {
		return createDataPacket();
	}
	
	public UtpPacket getFinPacket() {
		UtpPacket fin = createDataPacket();
		fin.setTypeVersion(UtpPacketUtils.ST_FIN);
		return fin;
	}

	@Override
	protected int readImpl(ByteBuffer dst) throws IOException {
		reader = new UtpReadingRunnable(this, dst, timeStamper);
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

	public void selectiveAckPacket(UtpPacket utpPacket, SelectiveAckHeaderExtension headerExtension, int timestampDifference, long advertisedWindow) throws IOException {
		UtpPacket sack = createSelectiveAckPacket(utpPacket, headerExtension, timestampDifference, advertisedWindow);
		sendPacket(sack);
		
	}

	private UtpPacket createSelectiveAckPacket(UtpPacket utpPacket,
			SelectiveAckHeaderExtension headerExtension, int timestampDifference, long advertisedWindow) {
		UtpPacket ackPacket = new UtpPacket();
		ackPacket.setAckNumber(longToUshort(getAckNumber()));
		ackPacket.setTimestampDifference(timestampDifference);
		ackPacket.setTimestamp(timeStamper.utpTimeStamp());
		ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
		ackPacket.setWindowSize(longToUint(advertisedWindow));
		
		ackPacket.setFirstExtension(UtpPacketUtils.SELECTIVE_ACK);
		UtpHeaderExtension[] extensions = { headerExtension };
		ackPacket.setExtensions(extensions);
		ackPacket.setTypeVersion(UtpPacketUtils.ST_STATE);
		
		return ackPacket;		
	}
	
	public UtpSocketState getState() {
		return state;
	}
	
	public void setState(UtpSocketState state) {
		this.state = state;
	}
	
	public void setTimetamper(MicroSecondsTimeStamp stamp) {
		this.timeStamper = stamp;
	}

	public void finalizeConnection(UtpPacket fin) throws IOException {
		sendPacket(fin);
		setState(UtpSocketState.FIN_SEND);
		
	}

	@Override
	protected void closeImpl() {
		if (reciever != null) {
			reciever.graceFullInterrupt();			
		}
	}

	@Override
	public boolean isReading() {
		return (reader != null ? reader.isRunning() : false);
	}

	@Override
	public boolean isWriting() {
		return (writer != null ? writer.isRunning() : false);

	}

	public void ackAlreadyAcked(UtpPacket utpPacket, int timestampDifference, long windowSize) throws IOException {
		UtpPacket ackPacket = new UtpPacket();
		ackPacket.setAckNumber(utpPacket.getSequenceNumber());
		
		ackPacket.setTimestampDifference(timestampDifference);
		ackPacket.setTimestamp(timeStamper.utpTimeStamp());
		ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
		ackPacket.setTypeVersion(UtpPacketUtils.ST_STATE);
		ackPacket.setWindowSize(longToUint(windowSize));
		sendPacket(ackPacket);	
		
	}

	@Override
	public void sendPacket(DatagramPacket pkt) throws IOException {
		synchronized (sendLock) {
			getDgSocket().send(pkt);
		}
		
	}
	
	
}
