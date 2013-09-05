package ch.uzh.csg.utp4j.examples;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.futures.UtpConnectFuture;
import ch.uzh.csg.utp4j.channels.futures.UtpWriteFuture;

public class TestWrite {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		
		ByteBuffer buffer = ByteBuffer.allocate(150000000);
		RandomAccessFile file     = new RandomAccessFile("testData/testData.zip", "rw");
		FileChannel  fileChannel = file.getChannel();
		int bytesRead = 0;
		System.out.println("start reading from file");
		do {
			bytesRead = fileChannel.read(buffer);
		} while(bytesRead != -1);
		System.out.println("file read");

		UtpSocketChannel chanel = UtpSocketChannel.open();
		UtpConnectFuture cFuture = chanel.connect(new InetSocketAddress("192.168.1.40", 13344));
//		UtpConnectFuture cFuture = chanel.connect(new InetSocketAddress("localhost", 13344));
//		UtpConnectFuture cFuture = chanel.connect(new InetSocketAddress("192.168.1.44", 13344));
		cFuture.block();
		
		UtpWriteFuture fut = chanel.write(buffer);
		fut.block();
		System.out.println("writing test done");
		chanel.close();

	}

}
