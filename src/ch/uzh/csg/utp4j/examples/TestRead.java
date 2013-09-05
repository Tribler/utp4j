package ch.uzh.csg.utp4j.examples;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import ch.uzh.csg.utp4j.channels.UtpServerSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.futures.UtpAcceptFuture;
import ch.uzh.csg.utp4j.channels.futures.UtpReadFuture;

public class TestRead {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		ByteBuffer buffer = ByteBuffer.allocate(150000000);
		UtpServerSocketChannel server = UtpServerSocketChannel.open();
		server.bind(new InetSocketAddress(13344));
		UtpAcceptFuture acceptFuture = server.accept();
		acceptFuture.block();
		UtpSocketChannel channel = acceptFuture.getChannel();
		UtpReadFuture readFuture = channel.read(buffer);
		readFuture.setListener(new SaveFileListener());
		readFuture.block();
		System.out.println("reading end");
		channel.close();
		server.close();


	}

}
