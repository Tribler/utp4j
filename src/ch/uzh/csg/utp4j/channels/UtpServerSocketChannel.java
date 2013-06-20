package ch.uzh.csg.utp4j.channels;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import ch.uzh.csg.utp4j.channels.impl.UtpPacketRecievable;
import ch.uzh.csg.utp4j.channels.impl.UtpRecieveRunnable;
import ch.uzh.csg.utp4j.channels.impl.UtpServerSocketChannelImpl;

public abstract class UtpServerSocketChannel {
	
	private DatagramSocket socket;
	protected boolean blocking = true;



	public static UtpServerSocketChannel open() {
		UtpServerSocketChannelImpl sChannel = new UtpServerSocketChannelImpl();	
		return sChannel;
	}
	
	
	protected abstract UtpSocketChannel acceptImpl() throws IOException;
	
	public UtpSocketChannel accept() throws IOException {
		return acceptImpl();		
	}
	
	
	public void bind(InetSocketAddress addr) throws IOException {
		try {
			setSocket(new DatagramSocket(addr));
			UtpServerSocketChannelImpl me = (UtpServerSocketChannelImpl) this;
			me.setListenRunnable(new UtpRecieveRunnable(this.getSocket(), me));
		} catch (IOException exp) {
			throw new IOException("Could not open UtpServerSocketChannel: " + exp.getMessage());
		}
	}


	public DatagramSocket getSocket() {
		return socket;
	}


	protected void setSocket(DatagramSocket socket) {
		this.socket = socket;
	}
	

}
