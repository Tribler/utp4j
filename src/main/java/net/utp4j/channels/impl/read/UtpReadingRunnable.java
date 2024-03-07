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
package net.utp4j.channels.impl.read;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.utp4j.channels.impl.UtpSocketChannelImpl;
import net.utp4j.channels.impl.UtpTimestampedPacketDTO;
import net.utp4j.channels.impl.alg.UtpAlgConfiguration;
import net.utp4j.channels.impl.close.UtpCloseFutureImpl;
import net.utp4j.data.MicroSecondsTimeStamp;
import net.utp4j.data.SelectiveAckHeaderExtension;
import net.utp4j.data.UtpPacket;
import net.utp4j.data.bytes.UnsignedTypesUtil;
/**
 * Reads incomming data. 
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public class UtpReadingRunnable extends Thread implements Runnable {
	
	private static final int PACKET_DIFF_WARP = 50000;
	private IOException exp;
	private final ByteBuffer buffer;
	private final UtpSocketChannelImpl channel;
	private final SkippedPacketBuffer skippedBuffer = new SkippedPacketBuffer();
	private boolean exceptionOccured = false;
	private boolean graceFullInterrupt;
	private boolean isRunning;
	private MicroSecondsTimeStamp timeStamper;
	private long totalPayloadLength = 0;
	private long lastPacketTimestamp;
	private int lastPayloadLength;
	private final UtpReadFutureImpl readFuture;
	private long nowtimeStamp;
	private long lastPackedRecieved;
	private final long startReadingTimeStamp;
	private boolean gotLastPacket = false;
	// in case we ack every x-th packet, this is the counter. 
	private int currentPackedAck = 0;
	
	private static final Logger log = LoggerFactory.getLogger(UtpReadingRunnable.class);
	
	public UtpReadingRunnable(UtpSocketChannelImpl channel, ByteBuffer buff, MicroSecondsTimeStamp timestamp, UtpReadFutureImpl future) {
		this.channel = channel;
		this.buffer = buff;
		this.timeStamper = timestamp;
		this.readFuture = future;
		lastPayloadLength = UtpAlgConfiguration.MAX_PACKET_SIZE;
		this.startReadingTimeStamp = timestamp.timeStamp();
	}
	
	public int getBytesRead() {
		return 0;
	}

	public IOException getException() {
		return exp;
	}

	public boolean hasExceptionOccured() {
		return false;
	}

	@Override
	public void run() {
//		buffer.flip();

		isRunning = true;
		IOException exp = null;
		while(continueReading()) {
			BlockingQueue<UtpTimestampedPacketDTO> queue = channel.getDataGramQueue();
			try {
				UtpTimestampedPacketDTO timestampedPair = queue.poll(UtpAlgConfiguration.TIME_WAIT_AFTER_LAST_PACKET/2, TimeUnit.MICROSECONDS);
				nowtimeStamp = timeStamper.timeStamp();
				if (timestampedPair != null) {
					currentPackedAck++;
//					log.debug("Seq: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));
					lastPackedRecieved = timestampedPair.stamp();
					if (isLastPacket(timestampedPair)) {
						gotLastPacket  = true;
						log.debug("GOT LAST PACKET");
						lastPacketTimestamp = timeStamper.timeStamp();
					}
					if (isPacketExpected(timestampedPair.utpPacket())) {
						handleExpectedPacket(timestampedPair);								
					} else {
						handleUnexpectedPacket(timestampedPair);
					}
					if (ackThisPacket()) {
						currentPackedAck = 0;
					}
				}
				
				/*TODO: How to measure Rtt here for dynamic timeout limit?*/
				if (isTimedOut()) {
					if (!hasSkippedPackets()) {
						gotLastPacket  = true;
						log.debug("ENDING READING, NO MORE INCOMMING DATA");
					} else {
						log.debug("now: " + nowtimeStamp + " last: " + lastPackedRecieved + " = " + (nowtimeStamp - lastPackedRecieved));
						log.debug("now: " + nowtimeStamp + " start: " + startReadingTimeStamp + " = " + (nowtimeStamp - startReadingTimeStamp));
						throw new IOException();						
					}
				}
					
			} catch (IOException ioe) {
				exp = ioe;
				exp.printStackTrace();
				exceptionOccured = true;
			} catch (InterruptedException iexp) {
				iexp.printStackTrace();
				exceptionOccured = true;
			} catch (ArrayIndexOutOfBoundsException aexp) {
				aexp.printStackTrace();
				exceptionOccured = true;
				exp = new IOException();
			}
		}
		isRunning = false;
		readFuture.finished(exp, buffer);
		
		
		log.debug("Buffer position: " + buffer.position() + " buffer limit: " + buffer.limit());
		log.debug("PAYLOAD LENGHT " + totalPayloadLength);
		log.debug("READER OUT");

		channel.returnFromReading();

	}
	
	private boolean isTimedOut() {
		//TODO: extract constants...
		/* time out after 4sec, when eof not reached */
		boolean timedOut = nowtimeStamp - lastPackedRecieved >= 4000000;
		/* but if remote socket has not recieved synack yet, he will try to reconnect
		 * await that aswell */
		boolean connectionReattemptAwaited = nowtimeStamp - startReadingTimeStamp >= 4000000;
		return timedOut && connectionReattemptAwaited;
	}

	private boolean isLastPacket(UtpTimestampedPacketDTO timestampedPair) {
//		log.debug("WindowSize: " + (timestampedPair.utpPacket().getWindowSize() & 0xFFFFFFFF));
//		log.debug("got Packet: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF) + " and will ack? " + ackThisPacket() + " acksizeCounter " + currentPackedAck);
		return (timestampedPair.utpPacket().getWindowSize() & 0xFFFFFFFF) == 0;
	}

	private void handleExpectedPacket(UtpTimestampedPacketDTO timestampedPair) throws IOException {
//		log.debug("handling expected packet: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));
		if (hasSkippedPackets()) {
			buffer.put(timestampedPair.utpPacket().getPayload());
			int payloadLength = timestampedPair.utpPacket().getPayload().length;
			lastPayloadLength = payloadLength;
			totalPayloadLength += payloadLength;
			Queue<UtpTimestampedPacketDTO> packets = skippedBuffer.getAllUntillNextMissing();
			int lastSeqNumber = 0;
			if (packets.isEmpty()) {
				lastSeqNumber = timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF;
			}
			UtpPacket lastPacket = null;
			for (UtpTimestampedPacketDTO p : packets) {
				buffer.put(p.utpPacket().getPayload());
				payloadLength += p.utpPacket().getPayload().length;
				lastSeqNumber = p.utpPacket().getSequenceNumber() & 0xFFFF;
				lastPacket = p.utpPacket();
			}
			skippedBuffer.reindex(lastSeqNumber);
			channel.setAckNumber(lastSeqNumber);
			//if still has skipped packets, need to selectively ack
			if (hasSkippedPackets()) {
				if (ackThisPacket()) {
//					log.debug("acking expected, had, still have");
					SelectiveAckHeaderExtension headerExtension = skippedBuffer.createHeaderExtension();
					channel.selectiveAckPacket(headerExtension, getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());								
				}

			} else {
				if (ackThisPacket()) {
//					log.debug("acking expected, has, than has nomore");
					channel.ackPacket(lastPacket, getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());					
				}
			}
		} else {
			if (ackThisPacket()) {
//				log.debug("acking expected, nomore");
				channel.ackPacket(timestampedPair.utpPacket(), getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());				
			} else {
				channel.setAckNumber(timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF);
			}
			buffer.put(timestampedPair.utpPacket().getPayload());
			totalPayloadLength += timestampedPair.utpPacket().getPayload().length;
		}
	}
	
	private boolean ackThisPacket() {
        return currentPackedAck >= UtpAlgConfiguration.SKIP_PACKETS_UNTIL_ACK;
    }

	/**
	 * Returns the average space available in the buffer in Bytes.
	 * @return bytes
	 */
	public long getLeftSpaceInBuffer() throws IOException {
		return (long) (skippedBuffer.getFreeSize()) * lastPayloadLength;
	}

	private int getTimestampDifference(UtpTimestampedPacketDTO timestampedPair) {
		int difference = timeStamper.utpDifference(timestampedPair.utpTimeStamp(), timestampedPair.utpPacket().getTimestamp());
		return difference;
	}

	private void handleUnexpectedPacket(UtpTimestampedPacketDTO timestampedPair) throws IOException {
		int expected = getExpectedSeqNr();
//		log.debug("handling unexpected packet: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));
		int seqNr = timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF;
		if (skippedBuffer.isEmpty()) {
			skippedBuffer.setExpectedSequenceNumber(expected);
		}
		//TODO: wrapping seq nr: expected can be 5 e.g.
		// but buffer can recieve 65xxx, which already has been acked, since seq numbers wrapped. 
		// current implementation puts this wrongly into the buffer. it should go in the else block
		// possible fix: alreadyAcked = expected > seqNr || seqNr - expected > CONSTANT;
		boolean alreadyAcked = expected > seqNr || seqNr - expected > PACKET_DIFF_WARP;
		
		boolean saneSeqNr = expected == skippedBuffer.getExpectedSequenceNumber();
//		log.debug("saneSeqNr: " + saneSeqNr + " alreadyAcked: " + alreadyAcked + " will ack: " + ackThisPacket());
		if (saneSeqNr && !alreadyAcked) {
			skippedBuffer.bufferPacket(timestampedPair);
			// need to create header extension after the packet is put into the incomming buffer. 
			SelectiveAckHeaderExtension headerExtension = skippedBuffer.createHeaderExtension();
			if (ackThisPacket()) {
//				log.debug("acking unexpected snae");
				channel.selectiveAckPacket(headerExtension, getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());									
			}
		} else if (ackThisPacket()){
			SelectiveAckHeaderExtension headerExtension = skippedBuffer.createHeaderExtension();
//			log.debug("acking unexpected  nonsane");
			channel.ackAlreadyAcked(headerExtension, getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());
		}
	}
	
	/**
	 * True if this packet is expected. 
	 * @param utpPacket packet
	 * @return
	 */
	public boolean isPacketExpected(UtpPacket utpPacket) {
		int seqNumberFromPacket = utpPacket.getSequenceNumber() & 0xFFFF;
//		log.debug("Expected Sequence Number == " + getExpectedSeqNr());
		return getExpectedSeqNr() == seqNumberFromPacket;
	}
	
	private int getExpectedSeqNr() {
		int ackNumber = channel.getAckNumber();
		if (ackNumber == UnsignedTypesUtil.MAX_USHORT) {
			return 1;
		} 
		return ackNumber + 1;
	}

	private boolean hasSkippedPackets() {
		return !skippedBuffer.isEmpty();
	}

	public void graceFullInterrupt() {
		this.graceFullInterrupt = true;
	}

	private boolean continueReading() {
		return !graceFullInterrupt && !exceptionOccured 
				&& (!gotLastPacket || hasSkippedPackets() || !timeAwaitedAfterLastPacket());
	}
		
	private boolean timeAwaitedAfterLastPacket() {
		return (timeStamper.timeStamp() - lastPacketTimestamp) > UtpAlgConfiguration.TIME_WAIT_AFTER_LAST_PACKET 
				&& gotLastPacket;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public MicroSecondsTimeStamp getTimestamp() {
		return timeStamper;
	}

	public void setTimestamp(MicroSecondsTimeStamp timestamp) {
		this.timeStamper = timestamp;
	}
}
