package ch.uzh.csg.utp4j.channels.impl.read;

import java.io.IOException;
import java.nio.ByteBuffer;

import ch.uzh.csg.utp4j.channels.futures.UtpReadFuture;

public class UtpReadFutureImpl extends UtpReadFuture {

	public UtpReadFutureImpl() throws InterruptedException {
		semaphore.acquire();
	}

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
					Thread listenerThread = new Thread(listener);
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
