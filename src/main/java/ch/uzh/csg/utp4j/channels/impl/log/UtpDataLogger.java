package ch.uzh.csg.utp4j.channels.impl.log;

import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.DEBUG;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.uzh.csg.utp4j.channels.impl.alg.OutPacketBuffer;

public class UtpDataLogger implements UtpStatisticLogger {

	public static String LOG_NAME = "log.csv";
	private int currentWindow;
	private long difference;
	private long minDelay;
	private long ourDelay;
	private long offTarget;
	private double delayFactor;
	private double windowFactor;
	private int gain;
	private int ackRecieved;
	private String sAck;
	private int maxWindow;
	private long minimumTimeStamp;
	private long timeStamp;
	private long packetRtt;
	private long rttVar;
	private long rtt;
	private int advertisedWindow;
	private int theirDifference;
	private long theirMinDelay;
	private int bytesSend;

	private boolean loggerOn = true;
	private RandomAccessFile logFile;
	private FileChannel fileChannel;

	private final static Logger log = LoggerFactory
			.getLogger(OutPacketBuffer.class);

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#currentWindow(int)
	 */
	@Override
	public void currentWindow(int currentWindow) {
		this.currentWindow = currentWindow;
	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#ourDifference(long)
	 */
	@Override
	public void ourDifference(long difference) {
		this.difference = difference;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#minDelay(long)
	 */
	@Override
	public void minDelay(long minDelay) {
		this.minDelay = minDelay;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#ourDelay(long)
	 */
	@Override
	public void ourDelay(long ourDelay) {
		this.ourDelay = ourDelay;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#offTarget(long)
	 */
	@Override
	public void offTarget(long offTarget) {
		this.offTarget = offTarget;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#delayFactor(double)
	 */
	@Override
	public void delayFactor(double delayFactor) {
		this.delayFactor = delayFactor;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#windowFactor(double)
	 */
	@Override
	public void windowFactor(double windowFactor) {
		this.windowFactor = windowFactor;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#gain(int)
	 */
	@Override
	public void gain(int gain) {
		this.gain = gain;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#ackRecieved(int)
	 */
	@Override
	public void ackRecieved(int seqNrToAck) {
		this.ackRecieved = seqNrToAck;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#sAck(int)
	 */
	@Override
	public void sAck(int sackSeqNr) {
		if (sAck != null) {
			sAck += " " + sackSeqNr;
		} else {
			sAck = "" + sackSeqNr;
		}

	}

	public UtpDataLogger() {
		openFile();
		writeEntry("TimeMicros;AckRecieved;CurrentWidow_Bytes;Difference_Micros;MinDelay_Micros;OurDelay_Micros;Their_Difference_Micros;"
				+ "Their_MinDelay_Micros;Their_Delay_Micros;OffTarget_Micros;DelayFactor;WindowFactor;Gain_Bytes;PacketRtt_Millis;"
				+ "RttVar_Millis;Rtt_Millis;AdvertisedWindow_Bytes;MaxWindow_Bytes;BytesSend;SACK\n");
		minimumTimeStamp = 0;
	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#next()
	 */
	@Override
	public void next() {
		if (DEBUG && loggerOn) {
			String logEntry = "" + (timeStamp - minimumTimeStamp) + ";";
			logEntry += ackRecieved + ";";
			logEntry += currentWindow + ";";
			logEntry += difference + ";";
			logEntry += minDelay + ";";
			logEntry += ourDelay + ";";
			logEntry += theirDifference + ";";
			logEntry += theirMinDelay + ";";
			logEntry += (theirDifference - theirMinDelay) + ";";
			logEntry += offTarget + ";";
			logEntry += delayFactor + ";";
			logEntry += windowFactor + ";";
			logEntry += gain + ";";
			logEntry += packetRtt + ";";
			logEntry += rttVar + ";";
			logEntry += rtt + ";";
			logEntry += advertisedWindow + ";";
			logEntry += maxWindow + ";";
			logEntry += bytesSend;
			if (sAck != null) {
				logEntry += ";(" + sAck + ")\n";
			} else {
				logEntry += "\n";
			}
			writeEntry(logEntry);
			sAck = null;
		}
	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#maxWindow(int)
	 */
	@Override
	public void maxWindow(int maxWindow) {
		this.maxWindow = maxWindow;

	}

	private void openFile() {
		if (DEBUG && loggerOn) {
			try {
				logFile = new RandomAccessFile("testData/" + LOG_NAME, "rw");
				fileChannel = logFile.getChannel();
				fileChannel.truncate(0);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void closeFile() {
		if (DEBUG && loggerOn) {
			try {
				logFile.close();
				fileChannel.close();
			} catch (IOException exp) {
				exp.printStackTrace();
			}
		}
	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#end(int)
	 */
	@Override
	public void end(int bytesLength) {
		if (DEBUG && loggerOn) {
			closeFile();
			long seconds = (timeStamp - minimumTimeStamp) / 1000000;
			long sendRate = 0;
			if (seconds != 0) {
				sendRate = (bytesLength / 1024) / seconds;
			}
			log.debug("SENDRATE: " + sendRate + "kB/sec");
		}
	}

	private void writeEntry(String entry) {
		if (DEBUG && loggerOn && entry != null) {
			ByteBuffer bbuffer = ByteBuffer.allocate(entry.getBytes().length);
			bbuffer.put(entry.getBytes());
			bbuffer.flip();
			try {
				while (bbuffer.hasRemaining()) {
					fileChannel.write(bbuffer);
				}
			} catch (IOException e) {
				log.error("failed to entry: " + entry);
			}
		}
	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#microSecTimeStamp(long)
	 */
	@Override
	public void microSecTimeStamp(long logTimeStampMillisec) {
		if (minimumTimeStamp == 0) {
			minimumTimeStamp = logTimeStampMillisec;
		}
		timeStamp = logTimeStampMillisec;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#pktRtt(long)
	 */
	@Override
	public void pktRtt(long packetRtt) {
		this.packetRtt = packetRtt;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#rttVar(long)
	 */
	@Override
	public void rttVar(long rttVar) {
		this.rttVar = rttVar;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#rtt(long)
	 */
	@Override
	public void rtt(long rtt) {
		this.rtt = rtt;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#advertisedWindow(int)
	 */
	@Override
	public void advertisedWindow(int advertisedWindowSize) {
		this.advertisedWindow = advertisedWindowSize;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#theirDifference(int)
	 */
	@Override
	public void theirDifference(int theirDifference) {
		this.theirDifference = theirDifference;

	}

	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#theirMinDelay(long)
	 */
	@Override
	public void theirMinDelay(long theirMinDelay) {
		this.theirMinDelay = theirMinDelay;

	}
	
	/* (non-Javadoc)
	 * @see ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticInterface#bytesSend(int)
	 */
	@Override
	public void bytesSend(int bytesSend){
		this.bytesSend = bytesSend;
	}

}
