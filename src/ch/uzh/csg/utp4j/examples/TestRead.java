package ch.uzh.csg.utp4j.examples;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import ch.uzh.csg.utp4j.channels.UtpServerSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketChannel;

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
		UtpSocketChannel channel = server.accept();
		channel.read(buffer);
		Thread.sleep(5000);


	}

}
