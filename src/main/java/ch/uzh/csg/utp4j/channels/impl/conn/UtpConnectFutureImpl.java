package ch.uzh.csg.utp4j.channels.impl.conn;

import java.io.IOException;

import ch.uzh.csg.utp4j.channels.futures.UtpConnectFuture;

public class UtpConnectFutureImpl extends UtpConnectFuture {
	
	public UtpConnectFutureImpl() throws InterruptedException {
		semaphore.acquire();
	}

	public void finished(IOException exp) {
		this.exception = exp;
		this.isDone = true;
		semaphore.release();
	}

}
