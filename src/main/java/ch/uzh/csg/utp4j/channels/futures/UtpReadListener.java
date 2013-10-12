package ch.uzh.csg.utp4j.channels.futures;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class UtpReadListener implements Runnable {

	protected ByteBuffer byteBuffer;
	protected IOException exception;
	protected boolean createExtraThread = false;
	protected Thread currentThread = null;

	public void setByteBuffer(ByteBuffer buffer) {
		this.byteBuffer = buffer;
	}

	public void setIOException(IOException exp) {
		this.exception = exp;
	}
	
	public ByteBuffer getByteBuffer() {
		return byteBuffer;
	}
	
	public IOException getIOException() {
		return exception;
	}
	
	public boolean createExtraThread() {
		return createExtraThread;
	}
	
	public void setCreateExtraThread(boolean value) {
		this.createExtraThread = value;
	}
	
	public Thread getCurrentThread() {
		return currentThread;
	}
	
	public boolean hasException() {
		return exception != null;
	}
	
	public boolean isSuccessfull() {
		return exception == null;
	}
	
	@Override
	public void run() {
		this.currentThread = Thread.currentThread();
		actionAfterReading();
	}
	
	public abstract void actionAfterReading();
}
