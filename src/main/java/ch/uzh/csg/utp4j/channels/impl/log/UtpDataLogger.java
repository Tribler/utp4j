package ch.uzh.csg.utp4j.channels.impl.log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;

import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration;

import com.sun.management.OperatingSystemMXBean;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.DEBUG;

public class UtpDataLogger {
	
	public static String LOG_NAME = "log.csv";
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
	private long packetRtt;
	private long rttVar;
	private long rtt;
	private int advertisedWindow;
	private OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
	private int theirDifference;
	private long theirMinDelay;  
	
	public void currentWindow(int currentWindow) {
		this.currentWindow = currentWindow;
	}

	public void ourDifference(long difference) {
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
		log.add("TimeMicros;AckRecieved;CurrentWidow_Bytes;Difference_Micros;MinDelay_Micros;OurDelay_Micros;Their_Difference_Micros;" +
				"Their_MinDelay_Micros;Their_Delay_Micros;OffTarget_Micros;DelayFactor;WindowFactor;Gain_Bytes;PacketRtt_Millis;" +
				"RttVar_Millis;Rtt_Millis;AdvertisedWindow_Bytes;MaxWindow_Bytes;CPU%;SACK\n");
		minimumTimeStamp = 0;
	}
	public void next() {
		if (DEBUG) {
			String logEntry = "" + (timeStamp - minimumTimeStamp) + ";";
			logEntry += "" + ackRecieved + ";";
			logEntry += "" + currentWindow + ";";
			logEntry += "" + difference + ";";
			logEntry += "" + minDelay + ";";
			logEntry += "" + ourDelay + ";";
			logEntry += "" + theirDifference + ";";
			logEntry += "" + theirMinDelay + ";";
			logEntry += "" + (theirDifference - theirMinDelay) + ";";
			logEntry += "" + offTarget + ";";
			logEntry += "" + delayFactor + ";";
			logEntry += "" + windowFactor + ";";
			logEntry += "" + gain + ";";
			logEntry += "" + packetRtt + ";";
			logEntry += "" + rttVar + ";";
			logEntry += "" + rtt + ";";
			logEntry += "" + advertisedWindow + ";";
			logEntry += "" + maxWindow + ";";
			logEntry += "" + osBean.getProcessCpuLoad();
			if (sAck != null) {
				logEntry += ";(" + sAck + ")\n";
			} else {
				logEntry += "\n";
			}
			log.add(logEntry);
			sAck = null;		
		}
	}

	public void maxWindow(int maxWindow) {
		this.maxWindow = maxWindow;
		
	}
	
	public void end(int bytesLength) {
		if (DEBUG) {
			RandomAccessFile aFile;
			try {
				aFile = new RandomAccessFile("testData/" + LOG_NAME, "rw");
				FileChannel inChannel = aFile.getChannel();
				inChannel.truncate(0);
				ByteBuffer bbuffer = ByteBuffer.allocate(examineBytes());
				for (String str : log) {
					bbuffer.put(str.getBytes());
				}
				bbuffer.flip();
				while(bbuffer.hasRemaining()) {
					inChannel.write(bbuffer);
				}
				long seconds = (timeStamp - minimumTimeStamp)/1000000;
				aFile.close();
				inChannel.close();
				long sendRate = 0;
				if (seconds != 0) {
					sendRate = (bytesLength/1024)/seconds;					
				}
				System.out.println("SENDRATE: " + sendRate + "kB/sec");
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		}
	}

	private int examineBytes() {
		int bytesLength = 0;
		for (String str : log) {
			bytesLength += str.getBytes().length;
		}
		return bytesLength + 100;
	}

	public void microSecTimeStamp(long logTimeStampMillisec) {
		if (minimumTimeStamp == 0) {
			minimumTimeStamp = logTimeStampMillisec;
		}
		timeStamp = logTimeStampMillisec;
		
	}

	public void pktRtt(long packetRtt) {
		this.packetRtt = packetRtt;
		
	}

	public void rttVar(long rttVar) {
		this.rttVar = rttVar;
		
	}

	public void rtt(long rtt) {
		this.rtt = rtt;
		
	}

	public void advertisedWindow(int advertisedWindowSize) {
		this.advertisedWindow = advertisedWindowSize;
		
	}

	public void theirDifference(int theirDifference) {
		this.theirDifference = theirDifference;
		
	}

	public void theirMinDelay(long theirMinDelay) {
		this.theirMinDelay = theirMinDelay;
		
	}



}
