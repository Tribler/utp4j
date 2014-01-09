/* Copyright 2013 Ivan Iljkic
*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License. You may obtain a copy of
* the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations under
* the License.
*/
package net.utp4j.examples;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import net.utp4j.channels.UtpSocketChannel;
import net.utp4j.channels.futures.UtpConnectFuture;
import net.utp4j.channels.futures.UtpWriteFuture;

public class TestWrite {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		
		ByteBuffer buffer = ByteBuffer.allocate(150000000);
		RandomAccessFile file     = new RandomAccessFile("testData/sc S01E01.avi", "rw");
		FileChannel  fileChannel = file.getChannel();
		int bytesRead = 0;
		System.out.println("start reading from file");
		do {
			bytesRead = fileChannel.read(buffer);
		} while(bytesRead != -1);
		System.out.println("file read");

		UtpSocketChannel chanel = UtpSocketChannel.open();
//		UtpConnectFuture cFuture = chanel.connect(new InetSocketAddress("192.168.1.40", 13344));
		UtpConnectFuture cFuture = chanel.connect(new InetSocketAddress("localhost", 13344));
//		UtpConnectFuture cFuture = chanel.connect(new InetSocketAddress("192.168.1.44", 13344));
		cFuture.block();
		
		UtpWriteFuture fut = chanel.write(buffer);
		fut.block();
		System.out.println("writing test done");
		chanel.close();

	}

}
