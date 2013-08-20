package ch.uzh.csg.utp4j.channels.impl.read;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

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
	private boolean finRecieved;
	private long payloadLength = 0;
	private long finRecievedTimestamp;
	
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
			BlockingQueue<UtpTimestampedPacketDTO> queue = channel.getDataGramQueue();
			try {
				UtpTimestampedPacketDTO timestampedPair = queue.take();
				if (timestampedPair != null) {
					if (isFinPacket(timestampedPair)) {
						finRecieved = true;
						finRecievedTimestamp = timestamp.timeStamp();
					}
					if (isPacketExpected(timestampedPair.utpPacket())) {
						handleExpectedPacket(timestampedPair);								
					} else {
						handleUnexpectedPacket(timestampedPair);
					}						
				}
					
			} catch (IOException | InterruptedException exp) {
				exp.printStackTrace();
			}
		}
		isRunning = false;
		
		try {
			buffer.flip();
			File outFile = new File("testData/c_sc S01E01.avi");
			FileChannel fchannel = new FileOutputStream(outFile).getChannel();
			fchannel.write(buffer);
			fchannel.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
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
			payloadLength += timestampedPair.utpPacket().getPayload().length;
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
		return (skippedBuffer.getFreeSize()) * UtpAlgorithm.MIN_PACKET_SIZE;
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
		return !graceFullInterrupt && !exceptionOccured && (!finRecieved || (finRecieved && hasSkippedPackets() && !timeAwaitedAfterFin()));
	}
	
	private boolean timeAwaitedAfterFin() {
		return (timestamp.timeStamp() - finRecievedTimestamp) > 2000*1000;
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
