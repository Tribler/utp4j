package ch.uzh.csg.utp4j.channels.impl.read;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import ch.uzh.csg.utp4j.channels.UtpSocketState;
import ch.uzh.csg.utp4j.channels.impl.UtpSocketChannelImpl;
import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgorithm;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.SelectiveAckHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

public class UtpReadingRunnable extends Thread implements Runnable {
	
	private IOException exp;
	private ByteBuffer buffer;
	private UtpSocketChannelImpl channel;
	private SkippedPacketBuffer skippedBuffer = new SkippedPacketBuffer();
	private boolean exceptionOccured = false;
	private boolean graceFullInterrupt;
	private boolean isRunning;
	private MicroSecondsTimeStamp timestamp;
	private long payloadLength = 0;
	private long finRecievedTimestamp;
	private int lastPayloadLength = UtpAlgorithm.MAX_PACKET_SIZE;
	private UtpReadFutureImpl readFuture;
	
	public UtpReadingRunnable(UtpSocketChannelImpl channel, ByteBuffer buff, MicroSecondsTimeStamp timestamp, UtpReadFutureImpl future) {
		this.channel = channel;
		this.buffer = buff;
		this.timestamp = timestamp;
		this.readFuture = future;
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
				UtpTimestampedPacketDTO timestampedPair = queue.take();
				if (timestampedPair != null) {
					if (isFinPacket(timestampedPair)) {
						channel.setState(UtpSocketState.GOT_FIN);
						finRecievedTimestamp = timestamp.timeStamp();
					}
					if (isPacketExpected(timestampedPair.utpPacket())) {
						handleExpectedPacket(timestampedPair);								
					} else {
						handleUnexpectedPacket(timestampedPair);
					}						
				}
					
			} catch (IOException ioe) {
				exp = ioe;
			} catch (InterruptedException iexp) {
				iexp.printStackTrace();
			}
		}
		isRunning = false;
		readFuture.finished(exp, buffer);
		
		
		System.out.println("Buffer position: " + buffer.position() + " buffer limit: " + buffer.limit());
		System.out.println("PAYLOAD LENGHT " + payloadLength);
		System.out.println("READER OUT");

	}
	
	private boolean isFinPacket(UtpTimestampedPacketDTO timestampedPair) {
		return timestampedPair.utpPacket().getTypeVersion() == UtpPacketUtils.ST_FIN;
	}

	private void handleExpectedPacket(UtpTimestampedPacketDTO timestampedPair) throws IOException {
		if (hasSkippedPackets()) {
			buffer.put(timestampedPair.utpPacket().getPayload());
			int payloadLength = timestampedPair.utpPacket().getPayload().length;
			lastPayloadLength = payloadLength;
			payloadLength += payloadLength;
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
			if (hasSkippedPackets()) {
				SelectiveAckHeaderExtension headerExtension = skippedBuffer.createHeaderExtension();
				channel.selectiveAckPacket(timestampedPair.utpPacket(), headerExtension, getTimestampDifference(timestampedPair), getWindowSize());			

			} else {
				channel.ackPacket(lastPacket, getTimestampDifference(timestampedPair), getWindowSize());
			}
		} else {
			channel.ackPacket(timestampedPair.utpPacket(), getTimestampDifference(timestampedPair), getWindowSize());
			buffer.put(timestampedPair.utpPacket().getPayload());
			payloadLength += timestampedPair.utpPacket().getPayload().length;

		}
	}
	
	private long getWindowSize() {
		return (skippedBuffer.getFreeSize()) * lastPayloadLength;
	}

	private int getTimestampDifference(UtpTimestampedPacketDTO timestampedPair) {
		int difference = timestamp.utpDifference(timestampedPair.utpTimeStamp(), timestampedPair.utpPacket().getTimestamp());
		return difference;
	}

	private void handleUnexpectedPacket(UtpTimestampedPacketDTO timestampedPair) throws IOException {
		int expected = channel.getAckNumber() + 1;
		if (skippedBuffer.isEmpty()) {
			skippedBuffer.setExpectedSequenceNumber(expected);
		}
		boolean isSmaller = expected > (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF);
		if (expected == skippedBuffer.getExpectedSequenceNumber() && !isSmaller) {
			skippedBuffer.bufferPacket(timestampedPair);
			SelectiveAckHeaderExtension headerExtension = skippedBuffer.createHeaderExtension();
			channel.selectiveAckPacket(timestampedPair.utpPacket(), headerExtension, getTimestampDifference(timestampedPair), getWindowSize());	
		} else {
			channel.ackAlreadyAcked(timestampedPair.utpPacket(), getTimestampDifference(timestampedPair), getWindowSize());
		}
	}
	
	public boolean isPacketExpected(UtpPacket utpPacket) {
		int seqNumberFromPacket = utpPacket.getSequenceNumber() & 0xFFFF;
		return (channel.getAckNumber() + 1) == seqNumberFromPacket;
	}
	
	private boolean hasSkippedPackets() {
		return !skippedBuffer.isEmpty();
	}

	public void graceFullInterrupt() {
		this.graceFullInterrupt = true;
	}

	private boolean continueReading() {
		return !graceFullInterrupt && !exceptionOccured 
				&& (!finRecieved() || (finRecieved() && hasSkippedPackets() && !timeAwaitedAfterFin()));
	}
	
	private boolean finRecieved() {
		return channel.getState() == UtpSocketState.GOT_FIN;
	}
	
	private boolean timeAwaitedAfterFin() {
		return (timestamp.timeStamp() - finRecievedTimestamp) > UtpAlgorithm.TIME_WAIT_AFTER_FIN;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public MicroSecondsTimeStamp getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(MicroSecondsTimeStamp timestamp) {
		this.timestamp = timestamp;
	}

}
