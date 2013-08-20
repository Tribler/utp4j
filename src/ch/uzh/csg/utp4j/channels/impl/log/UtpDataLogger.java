package ch.uzh.csg.utp4j.channels.impl.log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public class UtpDataLogger {
	
	private String current = new String();
	private ArrayList<String> log = new ArrayList<String>();
	private int currentWindow;
	private long difference;
	private long minDelay;
	private long ourDelay;
	private long offTarget;
	private double delayFactor;
	private double windowFactor;
	private int gain;
	private int ackRecieved;
	private Object sAck;
	private int maxWindow;
	private long minimumTimeStamp;
	private long timeStamp;

	
	public void currentWindow(int currentWindow) {
		this.currentWindow = currentWindow;
	}

	public void difference(long difference) {
		this.difference = difference;
		
	}

	public void minDelay(long minDelay) {
		this.minDelay = minDelay;
		
	}

	public void ourDelay(long ourDelay) {
		this.ourDelay = ourDelay;
		
	}

	public void offTarget(long offTarget) {
		this.offTarget = offTarget;
		
	}

	public void delayFactor(double delayFactor) {
		this.delayFactor = delayFactor;
		
	}

	public void windowFactor(double windowFactor) {
		this.windowFactor = windowFactor;
		
	}

	public void gain(int gain) {
		this.gain = gain;
		
	}

	public void ackRecieved(int seqNrToAck) {
		this.ackRecieved = seqNrToAck;
		
	}

	public void sAck(int sackSeqNr) {
		if (sAck != null) {
			sAck += " " + sackSeqNr;
		} else {
			sAck = "" + sackSeqNr;
 		}
		
	}

	public UtpDataLogger() {
		log.add("TimeMillis;AckRecieved;CurrentWidow;Difference;MinDelay;OurDelay;OffTarget;DelayFactor;WindowFactor;Gain;MaxWindow;SACK\n");
		minimumTimeStamp = 0;
	}
	public void next() {
		String logEntry = "" + (timeStamp - minimumTimeStamp) + ";";
		logEntry += "" + ackRecieved + ";";
		logEntry += "" + currentWindow + ";";
		logEntry += "" + difference + ";";
		logEntry += "" + minDelay + ";";
		logEntry += "" + ourDelay + ";";
		logEntry += "" + offTarget + ";";
		logEntry += "" +  + delayFactor + ";";
		logEntry += "" +  + windowFactor + ";";
		logEntry += "" +  + gain + ";";
		logEntry += "" +  + maxWindow;
		if (sAck != null) {
			logEntry += ";(" + sAck + ")\n";
		} else {
			logEntry += "\n";
		}
		log.add(logEntry);
		sAck = null;		
	}

	public void maxWindow(int maxWindow) {
		this.maxWindow = maxWindow;
		
	}
	
	public void end() {
		RandomAccessFile aFile;
		try {
			aFile = new RandomAccessFile("testData/log.csv", "rw");
			FileChannel inChannel = aFile.getChannel();
			ByteBuffer bbuffer = ByteBuffer.allocate(examineBytes());
			for (String str : log) {
				bbuffer.put(str.getBytes());
			}
			bbuffer.flip();
			while(bbuffer.hasRemaining()) {
				inChannel.write(bbuffer);
			}
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	private int examineBytes() {
		int bytesLength = 0;
		for (String str : log) {
			bytesLength += str.getBytes().length;
		}
		return bytesLength + 100;
	}

	public void milliSecTimeStamp(long logTimeStampMillisec) {
		if (minimumTimeStamp == 0) {
			minimumTimeStamp = logTimeStampMillisec;
		}
		timeStamp = logTimeStampMillisec;
		
	}



}
