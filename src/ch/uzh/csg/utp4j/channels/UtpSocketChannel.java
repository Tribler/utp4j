package ch.uzh.csg.utp4j.channels;

import static ch.uzh.csg.utp4j.channels.UtpSocketState.*;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_UINT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUint;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import java.util.Random;

import sun.org.mozilla.javascript.internal.ast.ReturnStatement;

import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;
import ch.uzh.csg.utp4j.data.bytes.exceptions.ByteOverflowException;


public class UtpSocketChannel implements Closeable, ByteChannel, Channel,
		GatheringByteChannel, ReadableByteChannel, WritableByteChannel {
	
	private DatagramChannel dataGramChannel;
	private int currentPayloadLength;
	private short connectionId;
	private MicroSecondsTimeStamp timeStamper = new MicroSecondsTimeStamp();
	private int sequenceNumber;
	private short connectionIdReciever;
	private SocketAddress remoteAddress;
	
	private int ackNumber;
	
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
		UtpSocketChannel c = new UtpSocketChannel();
		try {
			c.setDataGramChannel(DatagramChannel.open());
		} catch (IOException exp) {
			throw new IOException("Could not open UtpSocketChannel: " + exp.getMessage());
		}
		return c;
	}
	
	public void connect(SocketAddress address) throws IOException {
		try {
			
			remoteAddress = address;
			setupConnectionId();
			setSequenceNumber(DEF_SEQ_START);
			
			UtpPacket synPacket = UtpPacketUtils.createSynPacket();
			synPacket.setConnectionId(getConnectionIdReciever());
			synPacket.setTimestamp(timestamp());
			sendPacket(synPacket);
			
			incrementSequenceNumber();
			setState(CS_SYN_SENT);
			
			ByteBuffer ackBuffer = ByteBuffer.allocate(1024);
//			getDataGramChannel().receive(ackBuffer);
			
			
			
			
		} catch (IOException exp) {
			throw new IOException("Could not conenct to remote adress: " + exp.getMessage());
		}
	}
	
	private void incrementSequenceNumber() {
		setSequenceNumber(getSequenceNumber() + 1);
	}
	
	private void sendPacket(UtpPacket packet) throws IOException {
		if (packet != null) {
			ByteBuffer buffer = ByteBuffer.allocate(packet.getPacketLength());
			buffer.put(packet.toByteArray());
			buffer.flip();
			getDataGramChannel().send(buffer, getRemoteAdress());
		}
	}

	private void setupConnectionId() {
		Random rnd = new Random();
		int max = (int) (MAX_USHORT - 1);
		long rndInt = rnd.nextInt(max);
		setConnectionId(longToUshort(rndInt + 1));
		setConnectionIdReciever(longToUshort(rndInt));
		
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

	public DatagramChannel getDataGramChannel() {
		return dataGramChannel;
	}

	public void setDataGramChannel(DatagramChannel dataGramChannel) {
		this.dataGramChannel = dataGramChannel;
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
	
	public short getConnectionId() {
		return connectionId;
	}
	

	private void setConnectionId(short id) {
		connectionId = id;
		
	}

	private int timestamp() {
		int returnStamp;
		long stamp = timeStamper.utpTimeStamp();
		try {
			returnStamp  = longToUint(stamp);
		} catch (ByteOverflowException exp) {
			stamp = stamp % MAX_UINT;
			returnStamp = longToUint(stamp);
			System.out.println("Exception");
		}
		System.out.println(returnStamp);
		return returnStamp;
	}


	public short getConnectionIdReciever() {
		return connectionIdReciever;
	}

	public void setConnectionIdReciever(short connectionIdReciever) {
		this.connectionIdReciever = connectionIdReciever;
	}

	public UtpSocketState getState() {
		return state;
	}

	public void setState(UtpSocketState state) {
		this.state = state;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public void setRemoteAddress(SocketAddress adress) {
		remoteAddress = adress;
		
	}
	
	private SocketAddress getRemoteAdress() {
		return remoteAddress;
	}

	public void setupRandomSeqNumber() {
		Random rnd = new Random();
		int max = (int) (MAX_USHORT - 1);
		long rndInt = rnd.nextInt(max);
		//TODO: set here rnd seq number
		longToUshort(rndInt);
		
	}

	public int getAckNumber() {
		return ackNumber;
	}

	public void setAckNumber(int ackNumber) {
		this.ackNumber = ackNumber;
	}

	
}