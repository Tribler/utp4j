/* Copyright 2013 Ivan Iljkic
*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License. You may obtain a copy of
* the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations under
* the License.
*/
package ch.uzh.csg.utp4j.channels.impl.log;

import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.DEBUG;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.uzh.csg.utp4j.channels.impl.alg.OutPacketBuffer;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration;
/**
 * Data Logger. writes a csv file of the current transmission. when in debug mode {@see UtpAlgConfiguration.DEBUG)
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
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


	@Override
	public void currentWindow(int currentWindow) {
		this.currentWindow = currentWindow;
	}


	@Override
	public void ourDifference(long difference) {
		this.difference = difference;

	}


	@Override
	public void minDelay(long minDelay) {
		this.minDelay = minDelay;

	}


	@Override
	public void ourDelay(long ourDelay) {
		this.ourDelay = ourDelay;

	}


	@Override
	public void offTarget(long offTarget) {
		this.offTarget = offTarget;

	}


	@Override
	public void delayFactor(double delayFactor) {
		this.delayFactor = delayFactor;

	}


	@Override
	public void windowFactor(double windowFactor) {
		this.windowFactor = windowFactor;

	}


	@Override
	public void gain(int gain) {
		this.gain = gain;

	}


	@Override
	public void ackRecieved(int seqNrToAck) {
		this.ackRecieved = seqNrToAck;

	}


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


	@Override
	public void microSecTimeStamp(long logTimeStampMillisec) {
		if (minimumTimeStamp == 0) {
			minimumTimeStamp = logTimeStampMillisec;
		}
		timeStamp = logTimeStampMillisec;

	}


	@Override
	public void pktRtt(long packetRtt) {
		this.packetRtt = packetRtt;

	}


	@Override
	public void rttVar(long rttVar) {
		this.rttVar = rttVar;

	}


	@Override
	public void rtt(long rtt) {
		this.rtt = rtt;

	}


	@Override
	public void advertisedWindow(int advertisedWindowSize) {
		this.advertisedWindow = advertisedWindowSize;

	}


	@Override
	public void theirDifference(int theirDifference) {
		this.theirDifference = theirDifference;

	}


	@Override
	public void theirMinDelay(long theirMinDelay) {
		this.theirMinDelay = theirMinDelay;

	}
	

	@Override
	public void bytesSend(int bytesSend){
		this.bytesSend = bytesSend;
	}

}
