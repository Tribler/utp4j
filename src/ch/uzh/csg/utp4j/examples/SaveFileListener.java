package ch.uzh.csg.utp4j.examples;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import ch.uzh.csg.utp4j.channels.futures.UtpReadListener;

public class SaveFileListener implements UtpReadListener {
	
	public SaveFileListener(int i, boolean concat) {
		file = i;
		this.append = concat;
	}
	
	public SaveFileListener(int i) {
		file = i;
	}
	
	public SaveFileListener() {}
	public SaveFileListener(boolean concat) {
		this.append = concat;
	}
	
	private int file;
	private volatile IOException exp;
	private volatile ByteBuffer byteBuffer;
	private volatile boolean append = false;

	@Override
	public void run() {
		if (exp == null && byteBuffer != null) {
			try {
				byteBuffer.flip();
				File outFile = new File("testData/gotData_" + file + " .avi");
				FileOutputStream fileOutputStream = new FileOutputStream(outFile, append);
				FileChannel fchannel = fileOutputStream.getChannel();
				while(byteBuffer.hasRemaining()) {
					fchannel.write(byteBuffer);					
				}
				fchannel.close();
				fileOutputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (exp != null ) {
			exp.printStackTrace();
		}
		
	}

	@Override
	public void setByteBuffer(ByteBuffer buffer) {
		this.byteBuffer = buffer;
		
	}

	@Override
	public void setIOException(IOException exp) {
		this.exp = exp;
		
	}


}
