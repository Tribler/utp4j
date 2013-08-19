package ch.uzh.csg.utp4j.channels;

import static ch.uzh.csg.utp4j.channels.UtpSocketState.CLOSED;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.SYN_SENT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUint;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

import ch.uzh.csg.utp4j.channels.impl.UtpSocketChannelImpl;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;
import ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil;


public abstract class UtpSocketChannel implements Closeable, ByteChannel, Channel, ReadableByteChannel, WritableByteChannel {
	

	

	private long connectionIdSending;
	protected MicroSecondsTimeStamp timeStamper = new MicroSecondsTimeStamp();
	private int sequenceNumber;
	private long connectionIdRecieving;
	private SocketAddress remoteAddress;
	private int ackNumber;
	protected boolean isBlocking;
	private DatagramSocket dgSocket;
	
	
	/**
	 * Current State of the Socket. {@link UtpSocketState}
	 */
	protected UtpSocketState state = null;

	/**
	 * Sequencing begin.
	 */
	private static int DEF_SEQ_START = 1;

	protected UtpSocketChannel() {}
	
	public static UtpSocketChannel open() throws IOException {
		UtpSocketChannelImpl c = new UtpSocketChannelImpl();
		try {
			c.setDgSocket(new DatagramSocket());
			c.setState(CLOSED);
		} catch (IOException exp) {
			throw new IOException("Could not open UtpSocketChannel: " + exp.getMessage());
		}
		return c;
	}
	
	public void connect(SocketAddress address) throws IOException {
		try {
			
			connectImpl();
			setRemoteAddress(address);
			setupConnectionId();
			setSequenceNumber(DEF_SEQ_START);
			
			UtpPacket synPacket = UtpPacketUtils.createSynPacket();
			synPacket.setConnectionId(longToUshort(getConnectionIdRecieving()));
			synPacket.setTimestamp(timeStamper.utpTimeStamp());
			sendPacket(synPacket);
			setState(SYN_SENT);
			printState("[Syn send] ");
			incrementSequenceNumber();
		} catch (IOException exp) {
			setSequenceNumber(DEF_SEQ_START);
			setRemoteAddress(null);
			abortImpl();
			setState(CLOSED);
			throw new IOException("Could not conenct to remote adress: " + exp.getMessage());
		}
	}
	
	protected void printState(String msg) {
		String state = "[ConnID Sending: " + connectionIdSending + "] [ConnID Recv: " + connectionIdRecieving +
					   "] [SeqNr. " + sequenceNumber + "] [AckNr: " + ackNumber + "]";
		System.out.println(msg + state);
		
	}

	protected abstract void abortImpl();
	protected abstract void connectImpl();
	
	
	protected void incrementSequenceNumber() {
		int seqNumber = getSequenceNumber() + 1;
		if (seqNumber > MAX_USHORT) {
			seqNumber = 1;
		}
		setSequenceNumber(seqNumber);
	}
	
	protected void sendPacket(UtpPacket packet) throws IOException {
		if (packet != null) {
			byte[] utpPacketBytes = packet.toByteArray();
			int length = packet.getPacketLength();
			DatagramPacket pkt = new DatagramPacket(utpPacketBytes, length, getRemoteAdress());
			sendPacket(pkt);
		}
	}

	protected abstract void sendPacket(DatagramPacket pkt) throws IOException;

	private void setupConnectionId() {
		Random rnd = new Random();
		int max = (int) (MAX_USHORT - 1);
		long rndInt = rnd.nextInt(max);
		setConnectionIdRecieving(rndInt);
		setConnectionIdsending(rndInt + 1);
		
	}
	
	protected abstract int writeImpl(ByteBuffer src) throws IOException;
	
	
	@Override
	public int write(ByteBuffer src) throws IOException {
		return writeImpl(src);
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		return readImpl(dst);
	}


	protected abstract int readImpl(ByteBuffer dst) throws IOException;

	@Override
	public boolean isOpen() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void close() throws IOException {
		closeImpl();
		
	}
	
	protected abstract void closeImpl();
	
	public long getConnectionIdsending() {
		return connectionIdSending;
	}
	

	public void setConnectionIdsending(long connectionIdSending) {
		this.connectionIdSending = connectionIdSending;
		
	}

	
	protected UtpPacket createAckPacket(UtpPacket pkt, int timedifference, long advertisedWindow) {
		UtpPacket ackPacket = new UtpPacket();
		setAckNrFromPacketSqNr(pkt);
		ackPacket.setAckNumber(longToUshort(getAckNumber()));
		
		ackPacket.setTimestampDifference(timedifference);
		ackPacket.setTimestamp(timeStamper.utpTimeStamp());
		ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
		ackPacket.setTypeVersion(UtpPacketUtils.ST_STATE);
		ackPacket.setWindowSize(longToUint(advertisedWindow));
		return ackPacket;		
	}
	
	protected abstract void setAckNrFromPacketSqNr(UtpPacket utpPacket);
	
	protected UtpPacket createDataPacket() {
		UtpPacket pkt = new UtpPacket();
		pkt.setSequenceNumber(longToUshort(getSequenceNumber()));
		incrementSequenceNumber();
		pkt.setAckNumber(longToUshort(getAckNumber()));
		pkt.setConnectionId(longToUshort(getConnectionIdsending()));
		pkt.setTimestamp(timeStamper.utpTimeStamp());
		return pkt;
	}


	public long getConnectionIdRecieving() {
		return connectionIdRecieving;
	}

	public void setConnectionIdRecieving(long connectionIdRecieving) {
		this.connectionIdRecieving = connectionIdRecieving;
	}

	public UtpSocketState getState() {
		return state;
	}

	protected void setState(UtpSocketState state) {
		this.state = state;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	protected void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public void setRemoteAddress(SocketAddress remoteAdress) {
		remoteAddress = remoteAdress;
	}
	
	public SocketAddress getRemoteAdress() {
		return remoteAddress;
	}

	public int getAckNumber() {
		return ackNumber;
	}

	public void setAckNumber(int ackNumber) {
		this.ackNumber = ackNumber;
	}

	public DatagramSocket getDgSocket() {
		return dgSocket;
	}

	public void setDgSocket(DatagramSocket dgSocket) {
		if (this.dgSocket != null) {
			this.dgSocket.close();
		}
		this.dgSocket = dgSocket;
	}
	
	public boolean isConnected() {
		return getState() == UtpSocketState.CONNECTED;
	}

	public abstract boolean isReading();
	public abstract boolean isWriting();


}