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
package net.utp4j.channels.impl.read;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.utp4j.channels.futures.UtpReadFuture;

/**
 * Implements the future and hides details from the super class. 
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public class UtpReadFutureImpl extends UtpReadFuture {

	public UtpReadFutureImpl() throws InterruptedException {
		super(); 
	}

	/**
	 * Releasing semaphore and running the listener if set.
	 * @param exp
	 * @param buffer
	 */
	public void finished(IOException exp, ByteBuffer buffer) {
		this.buffer = buffer;
		this.exception = exp;
		isDone = true;
		semaphore.release();
		listenerLock.lock();
		try {
			if (listener != null) {
				listener.setByteBuffer(buffer);
				listener.setIOException(exp);
				if (listener.createExtraThread()) {
					Thread listenerThread = new Thread(listener, listener.getThreadName());
					listenerThread.start();
				} else {
					listener.run();
				}
			}
		} finally {
			listenerLock.unlock();
		}
	}
}
