package ch.uzh.csg.utp4j.channels.futures;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class UtpBlockableFuture {

	protected volatile boolean isDone;
	protected volatile IOException exception;
	protected volatile Semaphore semaphore = new Semaphore(1);
		
	
	public boolean isSuccessfull() {
		return exception == null;
	}

	public void block() throws InterruptedException {
		System.out.println("accept future blocking");
		semaphore.acquire();
		semaphore.release();
		System.out.println("accept future releasing");

	}

	public IOException getCause() {
		return exception;
	}

	public boolean isDone() {
		return isDone;
	}
	

	public void unblock() {
		semaphore.release();
	}

}
