package ch.uzh.csg.utp4j.channels.impl.read;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;

import ch.uzh.csg.utp4j.channels.impl.UtpSocketChannelImpl;
import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
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
	private boolean finRecieved;
	
	public UtpReadingRunnable(UtpSocketChannelImpl channel, ByteBuffer buff, MicroSecondsTimeStamp timestamp) {
		this.channel = channel;
		this.buffer = buff;
		this.timestamp = timestamp;
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
		while(continueReading()) {
			Queue<UtpTimestampedPacketDTO> queue = channel.getDataGramQueue();
			while(!queue.isEmpty()) {
				try {
					UtpTimestampedPacketDTO timestampedPair = queue.poll();
					if (timestampedPair != null) {
						if (isFinPacket(timestampedPair)) {
							finRecieved = true;
						}
						if (isPacketExpected(timestampedPair.utpPacket())) {
							handleExpectedPacket(timestampedPair);								
						} else {
							handleUnexpectedPacket(timestampedPair);
						}						
					}
					
				} catch (IOException exp) {
					exp.printStackTrace();
				}

			}
		}
		isRunning = false;
		System.out.println("READER OUT");

	}
	
	private boolean isFinPacket(UtpTimestampedPacketDTO timestampedPair) {
		return timestampedPair.utpPacket().getTypeVersion() == UtpPacketUtils.ST_FIN;
	}

	private void handleExpectedPacket(UtpTimestampedPacketDTO timestampedPair) throws IOException {
		if (hasSkippedPackets()) {
			buffer.put(timestampedPair.utpPacket().getPayload());
			Queue<UtpTimestampedPacketDTO> packets = skippedBuffer.getAllUntillNextMissing();
			int lastSeqNumber = 0;
			if (packets.isEmpty()) {
				lastSeqNumber = timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF;
			}
			UtpPacket lastPacket = null;
			for (UtpTimestampedPacketDTO p : packets) {
				buffer.put(p.utpPacket().getPayload());
				lastSeqNumber = p.utpPacket().getSequenceNumber() & 0xFFFF;
				lastPacket = p.utpPacket();
			}
			skippedBuffer.reindex(lastSeqNumber);
			channel.setAckNumber(lastSeqNumber);
			if (hasSkippedPackets()) {
				SelectiveAckHeaderExtension headerExtension = skippedBuffer.createHeaderExtension();
				channel.selectiveAckPacket(timestampedPair.utpPacket(), headerExtension, getTimestampDifference(timestampedPair));			
				System.out.println("acked with sack: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));

			} else {
				channel.ackPacket(lastPacket, getTimestampDifference(timestampedPair));
				System.out.println("acked: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));
			}
		} else {
			System.out.println("acked: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));
			channel.ackPacket(timestampedPair.utpPacket(), getTimestampDifference(timestampedPair));
			buffer.put(timestampedPair.utpPacket().getPayload());
		}
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
			channel.selectiveAckPacket(timestampedPair.utpPacket(), headerExtension, getTimestampDifference(timestampedPair));	
			System.out.println("acked with sack: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));
		} else {
			channel.ackAlreadyAcked(timestampedPair.utpPacket(), getTimestampDifference(timestampedPair));
			System.out.println("already acked: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));
		}
	}
	
	public boolean isPacketExpected(UtpPacket utpPacket) {
		int seqNumberFromPacket = utpPacket.getSequenceNumber() & 0xFFFF;
		System.out.println("RECIEVED: " + seqNumberFromPacket);
		return (channel.getAckNumber() + 1) == seqNumberFromPacket;
	}
	
	private boolean hasSkippedPackets() {
		return !skippedBuffer.isEmpty();
	}

	public void graceFullInterrupt() {
		this.graceFullInterrupt = true;
	}

	private boolean continueReading() {
		return !graceFullInterrupt && !exceptionOccured && (!finRecieved || (finRecieved && hasSkippedPackets()));
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
