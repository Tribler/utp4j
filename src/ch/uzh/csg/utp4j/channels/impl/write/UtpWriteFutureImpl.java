package ch.uzh.csg.utp4j.channels.impl.write;

import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import ch.uzh.csg.utp4j.channels.UtpWriteFuture;

public class UtpWriteFutureImpl extends UtpWriteFuture {
	
	private boolean isDone;
	private IOException exception;
	private int bytesWritten;
	private ReentrantLock lock = new ReentrantLock();
	private Condition finished;
	
	public UtpWriteFutureImpl() {
		finished = lock.newCondition();
	}

	@Override
	public boolean isSuccessfull() {
		return exception == null;
	}

	@Override
	public void block() throws InterruptedException {
		System.out.println("locking");
		finished.await();
	}

	@Override
	public IOException getCause() {
		return exception;
	}

	@Override
	public int getBytesSend() {
		return bytesWritten;
	}

	@Override
	public boolean isDone() {
		return isDone;
	}
	
	public void finished(IOException exp, int bytesWritten) {
		isDone = true;
		if (exp != null) {
			exception = exp;
		}
		this.bytesWritten = bytesWritten;
		unblock();
	}

	@Override
	public void unblock() {
		System.out.println("Unblocking writingFuture");
		finished.signal();		
	}

	public void setBytesSend(int position) {
		bytesWritten = position;
	}

}
