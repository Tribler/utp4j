package ch.uzh.csg.utp4j.channels;

import static ch.uzh.csg.utp4j.channels.UtpSocketState.SYN_SENT;
import static ch.uzh.csg.utp4j.channels.UtpSocketState.CLOSED;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_UINT;
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
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

import ch.uzh.csg.utp4j.channels.impl.UtpSocketChannelImpl;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;
import ch.uzh.csg.utp4j.data.bytes.exceptions.ByteOverflowException;


public abstract class UtpSocketChannel implements Closeable, ByteChannel, Channel,
		GatheringByteChannel, ReadableByteChannel, WritableByteChannel {
	

	
	private int currentPayloadLength;
	private short connectionIdSending;
	private MicroSecondsTimeStamp timeStamper = new MicroSecondsTimeStamp();
	private int sequenceNumber;
	private short connectionIdRecieving;
	private SocketAddress remoteAddress;
	private int ackNumber;
	
	private DatagramSocket dgSocket;
	
	
	/**
	 * Current State of the Socket. {@link UtpSocketState}
	 */
	private UtpSocketState state = null;

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
			synPacket.setConnectionId(getConnectionIdRecieving());
			synPacket.setTimestamp(timestamp());
			sendPacket(synPacket);
			incrementSequenceNumber();
			setState(SYN_SENT);

		} catch (IOException exp) {
			throw new IOException("Could not conenct to remote adress: " + exp.getMessage());
		}
	}
	
	protected abstract void connectImpl();
	
	protected void incrementSequenceNumber() {
		setSequenceNumber(getSequenceNumber() + 1);
	}
	
	protected void sendPacket(UtpPacket packet) throws IOException {
		if (packet != null) {
			byte[] utpPacketBytes = packet.toByteArray();
			int length = packet.getPacketLength();
			DatagramPacket pkt = new DatagramPacket(utpPacketBytes, length, getRemoteAdress());
			getDgSocket().send(pkt);
		}
	}

	private void setupConnectionId() {
		Random rnd = new Random();
		int max = (int) (MAX_USHORT - 1);
		long rndInt = rnd.nextInt(max);
		setConnectionIdRecieving(longToUshort(rndInt));
		setConnectionIdsending(longToUshort(rndInt + 1));
		
	}
	
	@Override
	public int write(ByteBuffer src) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long write(ByteBuffer[] srcs, int offset, int length)
			throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long write(ByteBuffer[] srcs) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}


	@Override
	public boolean isOpen() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		
	}
	
	public short getConnectionIdsending() {
		return connectionIdSending;
	}
	

	public void setConnectionIdsending(short connectionIdSending) {
		this.connectionIdSending = connectionIdSending;
		
	}

	protected int timestamp() {
		int returnStamp;
		long stamp = timeStamper.utpTimeStamp();
		try {
			returnStamp  = longToUint(stamp);
		} catch (ByteOverflowException exp) {
			stamp = stamp % MAX_UINT;
			returnStamp = longToUint(stamp);
			System.out.println("Exception");
		}
		return returnStamp;
	}
	
	protected int calculateTimestampDifference(int othertimestamp) {
		int nowTime = timestamp();
		long nowTimeL = (long) (nowTime & 0x00000000FFFFFFFF);
		long otherTimeL = (long) (othertimestamp & 0x00000000FFFFFFFF);
		long differenceL = nowTimeL - otherTimeL;
		return (int) (differenceL & 0xFFFFFFFF);
	}
	
	protected UtpPacket createAckPacket(UtpPacket pkt) {
		UtpPacket ackPacket = new UtpPacket();
		ackPacket.setSequenceNumber(longToUshort(getSequenceNumber()));
		incrementSequenceNumber();
		ackPacket.setAckNumber(longToUshort(getAckNumber()));
		int timedifference = calculateTimestampDifference(pkt.getTimestamp());
		
		ackPacket.setTimestampDifference(longToUint(timedifference));
		ackPacket.setTimestamp(timestamp());
		ackPacket.setConnectionId(getConnectionIdsending());
		
		return ackPacket;		
	}
	
	protected UtpPacket createDataPacket() {
		UtpPacket pkt = new UtpPacket();
		pkt.setSequenceNumber(longToUshort(getSequenceNumber()));
		incrementSequenceNumber();
		pkt.setAckNumber(longToUshort(getAckNumber()));
		pkt.setConnectionId(getConnectionIdsending());
		pkt.setTimestamp(timestamp());
		return pkt;
	}


	public short getConnectionIdRecieving() {
		return connectionIdRecieving;
	}

	public void setConnectionIdRecieving(short connectionIdRecieving) {
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

	
}