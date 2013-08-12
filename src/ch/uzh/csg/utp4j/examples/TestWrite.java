package ch.uzh.csg.utp4j.examples;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import ch.uzh.csg.utp4j.channels.UtpSocketChannel;

public class TestWrite {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		UtpSocketChannel chanel = UtpSocketChannel.open();
		chanel.connect(new InetSocketAddress("127.0.0.1", 13344));
		
		ByteBuffer buffer = ByteBuffer.allocate(150000000);
		RandomAccessFile file     = new RandomAccessFile("testData/sc S01E01.avi", "rw");
		FileChannel  fileChannel = file.getChannel();
		int bytesRead = 0;
		do {
			bytesRead = fileChannel.read(buffer);
		} while(bytesRead != -1);
		

		while(!chanel.isConnected()) { }
		
		chanel.write(buffer);
		Thread.sleep(2000);

	}

}
