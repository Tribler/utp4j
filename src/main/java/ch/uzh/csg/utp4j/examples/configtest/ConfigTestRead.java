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
package ch.uzh.csg.utp4j.examples.configtest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.uzh.csg.utp4j.channels.UtpServerSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.futures.UtpAcceptFuture;
import ch.uzh.csg.utp4j.channels.futures.UtpReadFuture;

public class ConfigTestRead {
	
	public static final Logger log = LoggerFactory.getLogger(ConfigTestRead.class);

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		ByteBuffer buffer = ByteBuffer.allocate(150000000);
		while(true) {
			UtpServerSocketChannel server = UtpServerSocketChannel.open();
			server.bind(new InetSocketAddress(13344));
			UtpAcceptFuture acceptFuture = server.accept();
			acceptFuture.block();
			UtpSocketChannel channel = acceptFuture.getChannel();
			UtpReadFuture readFuture = channel.read(buffer);
			readFuture.setListener(null);
			readFuture.block();
			log.debug("reading end");
			channel.close();
			server.close();
			server = null;
			channel = null;
			buffer.clear();
			readFuture = null;
			acceptFuture = null;
			Thread.sleep(5000);
		}

	}

}
