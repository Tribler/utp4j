package ch.uzh.csg.utp4j.channels;

import java.io.IOException;

public abstract class UtpWriteFuture  {
	
	public abstract boolean isSuccessfull();
	public abstract void block() throws InterruptedException;
	public abstract IOException getCause();
	public abstract int getBytesSend();
	public abstract boolean isDone();
	public abstract void unblock();
	
}
