package ch.uzh.csg.utp4j.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import ch.uzh.csg.utp4j.data.UtpPacket;

public class UtpServerSocketListenRunnable implements Runnable {

	private boolean blocking = true;
	private UtpServerSocketChannel channel;
	
	public boolean isBlocking() {
		return blocking;
	}

	public void setBlocking(boolean blocking) {
		this.blocking = blocking;
	}

	public UtpServerSocketListenRunnable(boolean blocking, UtpServerSocketChannel channel) {
		this.channel = channel;
		this.blocking = blocking;
	}
		
	@Override
	public void run() {
		ByteBuffer buf = ByteBuffer.allocate(1024);
		buf.clear();
		try {
			@SuppressWarnings("unused")
			SocketAddress adress = channel.getDgChannel().receive(buf);
			UtpPacket pkt = createPacket(buf);
			
			if (pkt.isSynPkt()) {
				UtpSocketChannel channel = UtpSocketChannel.open();
				channel.setRemoteAddress(adress);
				channel.setConnectionIdReciever((short) 1);
				channel.setupRandomSeqNumber();
				channel.setAckNumber(1);
				channel.setState(UtpSocketState.CS_CONNECTED);
			}
			
		} catch (IOException exp) {
			
		}
	}

	private UtpPacket createPacket(ByteBuffer buf) {
		UtpPacket pkt = new UtpPacket();
		byte[] pktb = new byte[20];
		buf.flip();
		buf.get(pktb);
		pkt.setFromByteArray(pktb);
		return pkt;
	}

}
