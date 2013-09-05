package ch.uzh.csg.utp4j.channels.futures;


public abstract class UtpWriteFuture extends UtpBlockableFuture{
	
	protected volatile int bytesWritten;

	public int getBytesSend() {
		return bytesWritten;
	}
	
}
