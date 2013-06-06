package com.utp4j.channels;

import static com.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static com.utp4j.channels.UtpSocketState.*;
import static com.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;
import static com.utp4j.data.bytes.UnsignedTypesUtil.longToUint;

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

import com.utp4j.data.MicroSecondsTimeStamp;
import com.utp4j.data.UtpPacket;
import com.utp4j.data.UtpPacketUtils;

public class UtpSocketChannel implements Closeable, ByteChannel, Channel,
		GatheringByteChannel, ReadableByteChannel, WritableByteChannel {
	
	private DatagramChannel dataGramChannel;
	private int currentPayloadLength;
	private short connectionId;
	private MicroSecondsTimeStamp timeStamper = new MicroSecondsTimeStamp();
	private int sequenceNumber;
	private short connectionIdReciever;
	
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
			
			getDataGramChannel().connect(address);
			setupConnectionId();
			setSequenceNumber(DEF_SEQ_START);
			
			UtpPacket synPacket = UtpPacketUtils.createSynPacket();
			synPacket.setConnectionId(getConnectionIdReciever());
			synPacket.setTimestamp(longToUint(timestamp()));
			
			ByteBuffer buffer = ByteBuffer.allocate(synPacket.getPacketLength());
			buffer.put(synPacket.toByteArray());
			getDataGramChannel().write(buffer);
			
			incrementSequenceNumber();
			setState(CS_SYN_SENT);
			
			
		} catch (IOException exp) {
			throw new IOException("Could not conenct to remote adress: " + exp.getMessage());
		}
	}
	
	private void incrementSequenceNumber() {
		setSequenceNumber(getSequenceNumber() + 1);
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

	private long timestamp() {
		return timeStamper.timeStamp();
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

	
}