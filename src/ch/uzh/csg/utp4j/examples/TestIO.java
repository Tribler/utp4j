package ch.uzh.csg.utp4j.examples;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

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
		
		ByteBuffer buffer = ByteBuffer.allocate(10000);
		byte[] array = createArray();
		buffer.put(array);
		while(!chanel.isConnected()) { }
		
		chanel.write(buffer);
		Thread.sleep(2000);
		chanel.close();
	}
	
	private static byte[] createArray() {
		byte[] array = new byte[10000];
		
		for (int i = 0; i < 10000; i++) {
			array[i] = (byte)(i%127);
		}
		
		return array;
	}

	private static class Listening extends Thread {
		
		UtpServerSocketChannel server;
		public Listening(UtpServerSocketChannel sckt) {
			super("listing thread");
			this.server = sckt;
		}
		
		@Override
		public void run() {
//			try {
//				UtpSocketChannel channel = server.accept();
//				ByteBuffer buff = ByteBuffer.allocate(10000);
//				channel.read(buff);
//				Thread.sleep(2000);
//				System.out.println(Arrays.toString(buff.array()));
//				channel.close();
//				server.close();
//				System.out.println("OUT");
//			} catch (IOException | InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
		}
	}

}
