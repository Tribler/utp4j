package ch.uzh.csg.utp4j.channels.impl;

import static ch.uzh.csg.utp4j.channels.UtpSocketState.CLOSED;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.CONNECTED;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.SYN_ACKING_FAILED;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.FIN;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.SELECTIVE_ACK;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.STATE;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.extractUtpPacket;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.isDataPacket;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.isFinPacket;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.isResetPacket;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.isStatePacket;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.isSynPkt;
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
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketState;
import ch.uzh.csg.utp4j.channels.futures.UtpCloseFuture;
import ch.uzh.csg.utp4j.channels.futures.UtpWriteFuture;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration;
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

public class UtpSocketChannelImpl extends UtpSocketChannel implements
		UtpPacketRecievable {

	private volatile BlockingQueue<UtpTimestampedPacketDTO> queue = new LinkedBlockingQueue<UtpTimestampedPacketDTO>();

	private UtpRecieveRunnable reciever;
	private UtpWritingRunnable writer;
	private UtpReadingRunnable reader;
	private Object sendLock = new Object();

	private UtpServerSocketChannelImpl server;
	private ScheduledExecutorService retryConnectionTimeScheduler;
	private int connectionAttempts = 0;


	private static final Logger log = LoggerFactory
			.getLogger(UtpSocketChannelImpl.class);

	@Override
	public void recievePacket(DatagramPacket udpPacket) {
		
		
		if (isSynAckPacket(udpPacket)) {
			handleSynAckPacket(udpPacket);
		} else if (isResetPacket(udpPacket)) {
			handleResetPacket(udpPacket);
		} else if (isSynPkt(udpPacket)) {
			handleIncommingConnectionRequest(udpPacket);
		} else if (isDataPacket(udpPacket)) {
			handlePacket(udpPacket);
		} else if (isStatePacket(udpPacket)) {
			handlePacket(udpPacket);
		} else if (isFinPacket(udpPacket)) {
			handleFinPacket(udpPacket);
		} else {
			sendResetPacket(udpPacket.getSocketAddress());
		}
			

		// if (UtpPacketUtils.isSynPkt(udpPacket)) {
		// handleIncommingConnectionRequest(udpPacket);
		// } else if (getState() == UtpSocketState.SYN_SENT) {
		// handleSynSendAck(udpPacket);
		// } else if (acceptDataPackets()) {
		// handlePacket(udpPacket);
		//
		// }
	}

	private void handleFinPacket(DatagramPacket udpPacket) {
		stateLock.lock();
		try {
			setState(UtpSocketState.GOT_FIN);
			UtpPacket finPacket = extractUtpPacket(udpPacket);
			long freeBuffer = 0;
			if (reader != null && reader.isRunning()) {
				freeBuffer = reader.getLeftSpaceInBuffer();
			} else {
				freeBuffer = UtpAlgConfiguration.MAX_PACKET_SIZE;
			}
			ackPacket(finPacket, timeStamper.utpDifference(finPacket.getTimestamp()), freeBuffer);
		} catch (IOException exp) {
			exp.printStackTrace();
			// TODO: what to do if exception?
		} finally {
			stateLock.unlock();
		}
	}

	private void handleResetPacket(DatagramPacket udpPacket) {
		// TODO Auto-generated method stub
		
	}

	private boolean isSynAckPacket(DatagramPacket udpPacket) {
		return isStatePacket(udpPacket) && getState() == UtpSocketState.SYN_SENT;
	}

	private void handleSynAckPacket(DatagramPacket udpPacket) {
		UtpPacket pkt = extractUtpPacket(udpPacket);
		if ((pkt.getConnectionId() & 0xFFFF) == getConnectionIdRecieving()) {
			stateLock.lock();
			setAckNrFromPacketSqNr(pkt);
			setState(CONNECTED);
			printState("[SynAck recieved] ");
			disableConnectionTimeOutCounter();
			connectFuture.finished(null);
			stateLock.unlock();			
		} else {
			sendResetPacket(udpPacket.getSocketAddress());
		}
	}
	
	private boolean isSameAddressAndId(long id, SocketAddress addr) {
		return (id & 0xFFFFFFFF) == getConnectionIdRecieving() 
				&& addr.equals(getRemoteAdress());
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
		UtpPacket utpPacket = extractUtpPacket(udpPacket);
		queue.offer(new UtpTimestampedPacketDTO(udpPacket, utpPacket,
				timeStamper.timeStamp(), timeStamper.utpTimeStamp()));

	}

	public void ackPacket(UtpPacket utpPacket, int timestampDifference,
			long windowSize) throws IOException {
		UtpPacket ackPacket = createAckPacket(utpPacket, timestampDifference,
				windowSize);
		sendPacket(ackPacket);
	}

	public void setupRandomSeqNumber() {
		Random rnd = new Random();
		int max = (int) (MAX_USHORT - 1);
		int rndInt = rnd.nextInt(max);
		setSequenceNumber(rndInt);

	}

	private void handleIncommingConnectionRequest(DatagramPacket udpPacket) {
		UtpPacket utpPacket = extractUtpPacket(udpPacket);

		if (acceptSyn(udpPacket)) {
			int timeStamp = timeStamper.utpTimeStamp();
			setRemoteAddress(udpPacket.getSocketAddress());
			setConnectionIdsFromPacket(utpPacket);
			setupRandomSeqNumber();
			setAckNrFromPacketSqNr(utpPacket);
			printState("[Syn recieved] ");
			int timestampDifference = timeStamper.utpDifference(timeStamp,
					utpPacket.getTimestamp());
			UtpPacket ackPacket = createAckPacket(utpPacket,
					timestampDifference,
					UtpAlgConfiguration.MAX_PACKET_SIZE * 1000);
			try {
				log.debug("sending syn ack");
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
		} else {
			sendResetPacket(udpPacket.getSocketAddress());
		}
	}

	private void sendResetPacket(SocketAddress addr) {
		log.debug("Sending RST packet");
		// TODO Auto-generated method stub
		
	}

	private boolean acceptSyn(DatagramPacket udpPacket) {
		UtpPacket pkt = extractUtpPacket(udpPacket);
		return getState() == CLOSED 
				|| (getState() == CONNECTED && isSameAddressAndId(
						pkt.getConnectionId(), udpPacket.getSocketAddress()));
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
		fin.setTypeVersion(FIN);
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

	public void selectiveAckPacket(UtpPacket utpPacket,
			SelectiveAckHeaderExtension headerExtension,
			int timestampDifference, long advertisedWindow) throws IOException {
		UtpPacket sack = createSelectiveAckPacket(utpPacket, headerExtension,
				timestampDifference, advertisedWindow);
		sendPacket(sack);

	}

	private UtpPacket createSelectiveAckPacket(UtpPacket utpPacket,
			SelectiveAckHeaderExtension headerExtension,
			int timestampDifference, long advertisedWindow) {
		UtpPacket ackPacket = new UtpPacket();
		ackPacket.setAckNumber(longToUshort(getAckNumber()));
		ackPacket.setTimestampDifference(timestampDifference);
		ackPacket.setTimestamp(timeStamper.utpTimeStamp());
		ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
		ackPacket.setWindowSize(longToUint(advertisedWindow));

		ackPacket.setFirstExtension(SELECTIVE_ACK);
		UtpHeaderExtension[] extensions = { headerExtension };
		ackPacket.setExtensions(extensions);
		ackPacket.setTypeVersion(STATE);

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

	@Override
	public UtpCloseFuture close() {
		abortImpl();
		if (isReading()) {
			reader.graceFullInterrupt();
		}
		if (isWriting()) {
			writer.graceFullInterrupt();
		}
		return null;
	}

	@Override
	public boolean isReading() {
		return (reader != null ? reader.isRunning() : false);
	}

	@Override
	public boolean isWriting() {
		return (writer != null ? writer.isRunning() : false);
	}

	public void ackAlreadyAcked(int expectingSeqNumber, int timestampDifference,
			long windowSize) throws IOException {
		UtpPacket ackPacket = new UtpPacket();
		ackPacket.setAckNumber(longToUshort(expectingSeqNumber));

		ackPacket.setTimestampDifference(timestampDifference);
		ackPacket.setTimestamp(timeStamper.utpTimeStamp());
		ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
		ackPacket.setTypeVersion(STATE);
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
			DatagramPacket pkt = new DatagramPacket(utpPacketBytes, length,
					getRemoteAdress());
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
		retryConnectionTimeScheduler = Executors
				.newSingleThreadScheduledExecutor();
		ConnectionTimeOutRunnable runnable = new ConnectionTimeOutRunnable(
				synPacket, this, stateLock);
		log.debug("starting scheduler");
		// retryConnectionTimeScheduler.schedule(runnable, 2, TimeUnit.SECONDS);
		retryConnectionTimeScheduler.scheduleWithFixedDelay(runnable,
				UtpAlgConfiguration.CONNECTION_ATTEMPT_INTERVALL_MILLIS,
				UtpAlgConfiguration.CONNECTION_ATTEMPT_INTERVALL_MILLIS,
				TimeUnit.MILLISECONDS);
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
	
	protected UtpPacket createAckPacket(UtpPacket pkt, int timedifference,
			long advertisedWindow) {
		UtpPacket ackPacket = new UtpPacket();
		setAckNrFromPacketSqNr(pkt);
		ackPacket.setAckNumber(longToUshort(getAckNumber()));

		ackPacket.setTimestampDifference(timedifference);
		ackPacket.setTimestamp(timeStamper.utpTimeStamp());
		ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
		ackPacket.setTypeVersion(STATE);
		ackPacket.setWindowSize(longToUint(advertisedWindow));
		return ackPacket;
	}
	
	protected UtpPacket createDataPacket() {
		UtpPacket pkt = new UtpPacket();
		pkt.setSequenceNumber(longToUshort(getSequenceNumber()));
		incrementSequenceNumber();
		pkt.setAckNumber(longToUshort(getAckNumber()));
		pkt.setConnectionId(longToUshort(getConnectionIdsending()));
		pkt.setTimestamp(timeStamper.utpTimeStamp());
		pkt.setTypeVersion(UtpPacketUtils.DATA);
		return pkt;
	}

}
