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

	public void next() {
		String logEntry = "[Ack: " + ackRecieved + "]\t";
		logEntry += "[CurrWindow: " + currentWindow + "]\t";
		logEntry += "[Diff: " + difference + "]\t";
		logEntry += "[MinDelay: " + minDelay + "]\t";
		logEntry += "[OurDelay: " + ourDelay + "]\t";
		logEntry += "[OffTarget: " + offTarget + "]\t";
		logEntry += "[DelayFactor: "  + delayFactor + "]\t";
		logEntry += "[WindowFactor: "  + windowFactor + "]\t";
		logEntry += "[Gain: "  + gain + "]\t";
		logEntry += "[MaxWindow: "  + maxWindow + "]\n";
		if (sAck != null) {
			logEntry += "[Sack: " + sAck + "]";
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
			aFile = new RandomAccessFile("testData/log.txt", "rw");
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



}
