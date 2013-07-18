package ch.uzh.csg.utp4j.examples;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.AllPermission;

import ch.uzh.csg.utp4j.channels.UtpServerSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketChannel;


public class TestIO {

	/**
	 * @param args
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		
		UtpServerSocketChannel server = null;
		server = UtpServerSocketChannel.open();
		server.bind(new InetSocketAddress(13345));
		(new Listening(server)).start();
		
		UtpSocketChannel chanel = UtpSocketChannel.open();
		chanel.connect(new InetSocketAddress("localhost", 13345));
		
		ByteBuffer buffer = ByteBuffer.allocate(1000);
		byte[] array = { 1, 1, 1, 1, 1, 1, 1,  2, 1, 1, 1, 1, 1, 1,  3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,  
				4, 1, 1, 1, 1, 1, 1,  5, 1, 1, 1, 1, 1, 1,  6, 1, 1, 1, 1, 1, 1,  7, 1, 1, 1, 1, 1, 1 ,  2, 1, 1, 1, 1, 1, 1,  
				3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,  2, 1, 1, 1, 1, 1, 1,  3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,
				3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,  2, 1, 1, 1, 1, 1, 1,  3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,
				3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,  2, 1, 1, 1, 1, 1, 1,  3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,
				3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,  2, 1, 1, 1, 1, 1, 1,  3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,
				3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,  2, 1, 1, 1, 1, 1, 1,  3, 1, 1, 1, 1, 1, 1,  4, 1, 1, 1, 1, 1, 1,};
		buffer.put(array);
		Thread.sleep(1000);
		
		chanel.write(buffer);

	}
	
	private static class Listening extends Thread {
		
		UtpServerSocketChannel server;
		public Listening(UtpServerSocketChannel sckt) {
			super("listing thread");
			this.server = sckt;
		}
		
		public void run() {
			try {
				while(true) {
					UtpSocketChannel channel = server.accept();
					ByteBuffer buff = ByteBuffer.allocate(1000);
					channel.read(buff);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
