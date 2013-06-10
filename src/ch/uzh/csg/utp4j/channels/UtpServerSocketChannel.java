package ch.uzh.csg.utp4j.channels;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import ch.uzh.csg.utp4j.data.UtpPacket;

public class UtpServerSocketChannel {
	
	private DatagramChannel dgChannel;
	private UtpServerSocketListenRunnable listenRunnable;



	public static UtpServerSocketChannel open() throws IOException {
		UtpServerSocketChannel sChannel = new UtpServerSocketChannel();
		sChannel.setListenRunnable(new UtpServerSocketListenRunnable(true, sChannel));
		
		try {
			DatagramChannel chan = DatagramChannel.open();
			chan.configureBlocking(true);
			sChannel.setDgChannel(chan);
		} catch (IOException exp) {
			throw new IOException("Could not open UtpServerSocketChannel: " + exp.getMessage());
		}
		
		return sChannel;
	}
	
	
	
	public UtpSocketChannel accept() throws IOException {
		Thread runner = new Thread(listenRunnable);
		runner.start();
		return null;
		
	}
	



	public void bind(InetSocketAddress addr) throws IOException {
		try {
			dgChannel.bind(addr);
		} catch (IOException exp) {
			throw new IOException("Could not open UtpServerSocketChannel: " + exp.getMessage());
		}
	}

	public DatagramChannel getDgChannel() {
		return dgChannel;
	}

	public void setDgChannel(DatagramChannel dgChannel) {
		this.dgChannel = dgChannel;
	}

	
	public UtpServerSocketListenRunnable getListenRunnable() {
		return listenRunnable;
	}
	
	
	
	public void setListenRunnable(UtpServerSocketListenRunnable listenRunnable) {
		this.listenRunnable = listenRunnable;
	}
}
