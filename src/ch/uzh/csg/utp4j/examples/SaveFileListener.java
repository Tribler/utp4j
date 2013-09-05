package ch.uzh.csg.utp4j.examples;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import ch.uzh.csg.utp4j.channels.futures.UtpReadListener;

public class SaveFileListener implements UtpReadListener {
	
	private volatile IOException exp;
	private volatile ByteBuffer byteBuffer;

	@Override
	public void run() {
		if (exp == null && byteBuffer != null) {
			try {
//				byteBuffer.flip();
				File outFile = new File("testData/c_sc S01E01.avi");
				FileChannel fchannel = new FileOutputStream(outFile).getChannel();
				fchannel.write(byteBuffer);
				fchannel.close();
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
