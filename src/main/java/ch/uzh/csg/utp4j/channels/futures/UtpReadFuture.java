package ch.uzh.csg.utp4j.channels.futures;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

public class UtpReadFuture extends UtpBlockableFuture {
	
	public UtpReadFuture() throws InterruptedException {
		super();
	}

	protected volatile ByteBuffer buffer;
	protected volatile UtpReadListener listener;
	protected final ReentrantLock listenerLock = new ReentrantLock();
	
	public ByteBuffer getBuffer() {
		return buffer;
	}
	
	public void setListener(UtpReadListener listener) {
		listenerLock.lock();
		this.listener = listener;
		listenerLock.unlock();
		
	}
	
	public int getBytesRead() {
		if (buffer != null) {
			return buffer.position();
		}
		return 0;
	}
	
}
