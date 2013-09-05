package ch.uzh.csg.utp4j.channels.futures;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface UtpReadListener extends Runnable {

	void setByteBuffer(ByteBuffer buffer);

	void setIOException(IOException exp);
	
}
