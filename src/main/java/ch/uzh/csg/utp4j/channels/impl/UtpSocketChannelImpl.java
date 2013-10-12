package ch.uzh.csg.utp4j.channels.impl;

import static ch.uzh.csg.utp4j.channels.UtpSocketState.CLOSED;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.CONNECTED;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.SYN_ACKING_FAILED;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUint;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketState;
import ch.uzh.csg.utp4j.channels.exception.ChannelStateException;
import ch.uzh.csg.utp4j.channels.futures.UtpWriteFuture;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgorithm;
import ch.uzh.csg.utp4j.channels.impl.conn.ConnectionTimeOutRunnable;
import ch.uzh.csg.utp4j.channels.impl.conn.UtpConnectFutureImpl;
import ch.uzh.csg.utp4j.channels.impl.read.UtpReadFutureImpl;
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

	private UtpServerSocketChannelImpl server;
	private ScheduledExecutorService retryConnectionTimeScheduler;
	private int connectionAttempts = 0;
	
	private static final Logger log = LoggerFactory.getLogger(UtpSocketChannelImpl.class);
	
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
		stateLock.lock();
		UtpPacket pkt = UtpPacketUtils.extractUtpPacket(udpPacket);
		setAckNrFromPacketSqNr(pkt);
		setState(CONNECTED);
		printState("[SynAck recieved] ");
		disableConnectionTimeOutCounter();
		connectFuture.finished(null);
		stateLock.unlock();
	}

	private void disableConnectionTimeOutCounter() {
		if (retryConnectionTimeScheduler != null) {
			retryConnectionTimeScheduler.shutdown();
			retryConnectionTimeScheduler = null;
		}
		connectionAttempts = 0;
	}
	
	public int getConnectionAttempts() {
		return connectionAttempts;
	}
	
	public void incrementConnectionAttempts() {
		connectionAttempts++;
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
//		if (getState() != CLOSED) {
//			ChannelStateException exp = new ChannelStateException("Recieved Syn Packet but Channel is already connected");
//			exp.setState(getState());
//			exp.setAddresse(udpPacket.getSocketAddress());
//			throw exp;
//		}
		
		UtpPacket utpPacket = UtpPacketUtils.extractUtpPacket(udpPacket);
		setRemoteAddress(udpPacket.getSocketAddress());
		setConnectionIdsFromPacket(utpPacket);
		setupRandomSeqNumber();
		setAckNrFromPacketSqNr(utpPacket);
		printState("[Syn recieved] ");
		int timestampDifference = timeStamper.utpDifference(timeStamp, utpPacket.getTimestamp());
		UtpPacket ackPacket = createAckPacket(utpPacket, timestampDifference, UtpAlgConfiguration.MAX_PACKET_SIZE*1000);
		try {
			sendPacket(ackPacket);			
			setState(CONNECTED);
 		} catch (IOException exp) {
 			// TODO: In future?
 			setRemoteAddress(null);
 			setConnectionIdsending((short) 0);
 			setConnectionIdRecieving((short) 0);
 			setAckNumber(0);
 			setState(SYN_ACKING_FAILED);
 			exp.printStackTrace();
 		}
		
	}

	@Override
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
	protected void connectImpl(UtpConnectFutureImpl future) {
		reciever = new UtpRecieveRunnable(getDgSocket(), this);
		reciever.start();
		
	}

	@Override
	protected void abortImpl() {
		if (reciever != null) {
			reciever.graceFullInterrupt();			
		} else if (server != null) {
			server.unregister(this);
		}
	}

	@Override
	public UtpWriteFuture write(ByteBuffer src) {
		UtpWriteFutureImpl future = null;
		try {
			future = new UtpWriteFutureImpl();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		writer = new UtpWritingRunnable(this, src, timeStamper, future);
		writer.start();			
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
	public UtpReadFutureImpl read(ByteBuffer dst) {
		UtpReadFutureImpl readFuture = null;
		try {
			readFuture = new UtpReadFutureImpl();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		reader = new UtpReadingRunnable(this, dst, timeStamper, readFuture);
		reader.start();
		return readFuture;
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
	
	@Override
	public UtpSocketState getState() {
		return state;
	}
	
	@Override
	public void setState(UtpSocketState state) {
		this.state = state;
	}
	
	public void finalizeConnection(UtpPacket fin) throws IOException {
		sendPacket(fin);
		setState(UtpSocketState.FIN_SEND);
		
	}

	@Override
	public void close() {
		abortImpl();
		if (isReading()) {
			reader.graceFullInterrupt();
		} 
		if (isWriting()) {
			writer.graceFullInterrupt();
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
	
	/* general method to send a packet, will be wrapped by a UDP Packet */
	@Override
	public void sendPacket(UtpPacket packet) throws IOException {
		if (packet != null) {
			byte[] utpPacketBytes = packet.toByteArray();
			int length = packet.getPacketLength();
			DatagramPacket pkt = new DatagramPacket(utpPacketBytes, length, getRemoteAdress());
			sendPacket(pkt);
		}
	}
	
	@Override
	public void setDgSocket(DatagramSocket dgSocket) {
		if (this.dgSocket != null) {
			this.dgSocket.close();
		}
		this.dgSocket = dgSocket;
	}
	
	@Override
	public void setAckNumber(int ackNumber) {
		this.ackNumber = ackNumber;
	}

	public void setServer(UtpServerSocketChannelImpl utpServerSocketChannelImpl) {
		this.server = utpServerSocketChannelImpl;
		
	}

	@Override
	public void setRemoteAddress(SocketAddress remoteAdress) {
		this.remoteAddress = remoteAdress;	
	}

	public void removeReader() {
		reader = null;
	}
	
	public void removeWriter() {
		writer = null;
	}

	@Override
	protected void startConnectionTimeOutCounter(UtpPacket synPacket) {
		retryConnectionTimeScheduler = Executors.newSingleThreadScheduledExecutor();
		ConnectionTimeOutRunnable runnable = new ConnectionTimeOutRunnable(synPacket, this, stateLock);
		log.debug("starting scheduler");
//		retryConnectionTimeScheduler.schedule(runnable, 2, TimeUnit.SECONDS);
		retryConnectionTimeScheduler.scheduleWithFixedDelay(runnable, 
				UtpAlgConfiguration.CONNECTION_ATTEMPT_INTERVALL_MILLIS, 
				UtpAlgConfiguration.CONNECTION_ATTEMPT_INTERVALL_MILLIS, TimeUnit.MILLISECONDS);
	}

	public void connectionFailed(IOException exp) {
		log.debug("got failing message");
		setSequenceNumber(DEF_SEQ_START);
		setRemoteAddress(null);
		abortImpl();
		setState(CLOSED);
		retryConnectionTimeScheduler.shutdown();
		retryConnectionTimeScheduler = null;
		connectFuture.finished(exp);
		
	}

	public void resendSynPacket(UtpPacket synPacket) {
		stateLock.lock();
		try {
			int attempts = getConnectionAttempts();
			log.debug("attempt: " + attempts);
			if (getState() == UtpSocketState.SYN_SENT) {
				try {
					if (attempts < UtpAlgConfiguration.MAX_CONNECTION_ATTEMPTS) {
						incrementConnectionAttempts();
						log.debug("REATTEMPTING CONNECTION");
						sendPacket(synPacket);
					} else {
						connectionFailed(new SocketTimeoutException());
					}
				} catch (IOException e) {
					if (attempts >= UtpAlgConfiguration.MAX_CONNECTION_ATTEMPTS) {
						connectionFailed(e);
					} // else ignore, try in next attempt
				}
			}
		} finally {
			stateLock.unlock();
		}
		
	}

	public void setTimetamper(MicroSecondsTimeStamp stamp) {
		this.timeStamper = stamp;
		
	}
	
	
}
