package ch.uzh.csg.utp4j.channels;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import ch.uzh.csg.utp4j.channels.futures.UtpAcceptFuture;
import ch.uzh.csg.utp4j.channels.impl.UtpServerSocketChannelImpl;
import ch.uzh.csg.utp4j.channels.impl.recieve.UtpRecieveRunnable;
/**
 * Server class interface
 * 
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public abstract class UtpServerSocketChannel {
	
	/*underlying socket*/
	private DatagramSocket socket;

	/**
	 * Opens and returns a server
	 * @return server {@link UtpServerSocketChannel}
	 */
	public static UtpServerSocketChannel open() {
		UtpServerSocketChannelImpl sChannel = new UtpServerSocketChannelImpl();	
		return sChannel;
	}
	
	/*Subclasses implement this methods*/
	protected abstract UtpAcceptFuture acceptImpl() throws IOException;
	
	/**
	 * Listens on incomming Connection requests
	 * @return {@link UtpAcceptFuture}
	 * @throws IOException
	 */
	public UtpAcceptFuture accept() throws IOException {
		return acceptImpl();		
	}
	
	/**
	 * Binds the server to an {@link DatagramSocket} and binds {@link InetSocketAddress}
	 * @param address
	 * @throws IOException see {@link DatagramSocket} Constructor Details
	 */
	public void bind(InetSocketAddress addr) throws IOException {
		setSocket(new DatagramSocket(addr));
		UtpServerSocketChannelImpl me = (UtpServerSocketChannelImpl) this;
		me.setListenRunnable(new UtpRecieveRunnable(this.getSocket(), me));
	}


	protected DatagramSocket getSocket() {
		return socket;
	}


	protected void setSocket(DatagramSocket socket) {
		this.socket = socket;
	}
	
	/**
	 * Closes this server and unbinds the underlying UDP Socket.
	 * The server cannot be closed while there are still open Channels.
	 */
	public abstract void close();

}
