package ch.uzh.csg.utp4j.channels.impl.alg;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Queue;


import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.channels.impl.log.UtpDataLogger;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.SelectiveAckHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;
import ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil;

public class UtpAlgorithm {
	/**
	 * TWEAKING SECTION
	 */
	
	public static int MINIMUM_TIMEOUT_MILLIS = 500;
	
	/**
	 * Packet size modus
	 */
	public PacketSizeModus packetSizeModus = PacketSizeModus.CONSTANT_1472;
	
	/**
	 * maximum packet size
	 * should be dynamically set once path mtu discovery implemented. 
	 */
	
	public static int MAX_PACKET_SIZE = 1472;
	
	/**
	 * minimum packet size. 
	 */
	public static int MIN_PACKET_SIZE = 150;
	
	/**
	 * Minimum path MTU
	 */
	public static int MINIMUM_MTU = 576;
	/**
	 * Maximal window increase per RTT - increase to allow uTP throttle up faster.
	 */
	public static int MAX_CWND_INCREASE_PACKETS_PER_RTT = 3000;
	
	/**
	 * maximal buffering delay
	 */
	public static int C_CONTROL_TARGET_MS = 1000000;
	
	/**
	 * activate burst sending
	 */
	public static boolean SEND_IN_BURST = true;
	
	/**
	 * Reduce burst sending artificially
	 */
	public static int MAX_BURST_SEND = 2;
	
	/**
	 * Minimum number of acks past seqNr=x to trigger a resend of seqNr=x;
	 */
	public static final int MIN_SKIP_PACKET_BEFORE_RESEND = 3;
		
	
	/**
	 * TWEAKING SECTION END
	 */
	
	/**
	 * Variables
	 */
	private int currentWindow = 0;
	private int maxWindow = MAX_CWND_INCREASE_PACKETS_PER_RTT;
	private MinimumDelay minDelay = new MinimumDelay();
	private OutPacketBuffer buffer;
	private MicroSecondsTimeStamp timeStamper;
	private int currentAckPosition = 0;
	private int currentBurstSend = 0;

	private long rtt = MINIMUM_TIMEOUT_MILLIS*2;
	private long rttVar = 0;
	

	private int advertisedWindowSize;

	private UtpDataLogger logger = new UtpDataLogger();

	private long lastTimeWindowReduced;

	
	public UtpAlgorithm(MicroSecondsTimeStamp timestamper, SocketAddress addr) {
		timeStamper = timestamper;
		buffer = new OutPacketBuffer(timestamper);
		buffer.setRemoteAdress(addr);
	}


	public void ackRecieved(UtpTimestampedPacketDTO pair) {
		int seqNrToAck = pair.utpPacket().getAckNumber() & 0xFFFF;
		long timestamp = timeStamper.timeStamp();
		int advertisedWindo = pair.utpPacket().getWindowSize() & 0xFFFFFFFF;
		updateAdvertisedWindowSize(advertisedWindo);
		logger.ackRecieved(seqNrToAck);
		int packetSizeJustAcked = buffer.markPacketAcked(seqNrToAck, timestamp);
		if (packetSizeJustAcked > 0) {
			updateRtt(timestamp, seqNrToAck);
			updateWindow(pair.utpPacket(), timestamp, packetSizeJustAcked);
		} 

		if (pair.utpPacket().getExtensions() != null) {
			SelectiveAckHeaderExtension selAck = findSelectiveAckExtension(pair.utpPacket());
			byte[] bitMask = selAck.getBitMask();
			for (int i = 0; i < bitMask.length; i++) {
				for (int j = 2; j < 9; j++) {
					if (SelectiveAckHeaderExtension.isBitMarked(bitMask[i], j)) {
						timestamp = timeStamper.timeStamp();
						int sackSeqNr = i*8 + j + seqNrToAck;
						logger.sAck(sackSeqNr);
						buffer.markPacketAcked(sackSeqNr, timestamp);	
						packetSizeJustAcked = buffer.markPacketAcked(seqNrToAck, timestamp);
						if (packetSizeJustAcked > 0) {
							updateRtt(timestamp, sackSeqNr);
							updateWindow(pair.utpPacket(), timestamp, packetSizeJustAcked);
						} 
					}
				}
			}
		}
		logger.next();
		
	}
		
	private void updateRtt(long timestamp, int seqNrToAck) {
		long sendTimeStamp = buffer.getSendTimeStamp(seqNrToAck);	
		if (sendTimeStamp != -1) {
			long packetRtt = (timestamp-sendTimeStamp)/1000;;
			long delta = rtt - packetRtt;
			rttVar += (Math.abs(delta) - rttVar)/4;
			rtt += (packetRtt - rtt)/8;
			logger.pktRtt(packetRtt);
			logger.rttVar(rttVar);
			logger.rtt(rtt);
		}
	}


	private void updateAdvertisedWindowSize(int advertisedWindo) {
		this.advertisedWindowSize = advertisedWindo;
		
	}


	private void checkTimeouts() {
		// TODO: check if packets timed out
	}

	
	private void updateWindow(UtpPacket utpPacket, long timestamp, int packetSizeJustAcked) {
		long logTimeStampMillisec = timeStamper.timeStamp()/1000;
		logger.milliSecTimeStamp(logTimeStampMillisec);
		currentWindow = buffer.getBytesOnfly();
		logger.currentWindow(currentWindow);
		long difference = utpPacket.getTimestampDifference() & 0xFFFFFFFF;
		logger.difference(difference);
		updateMinDelay(difference, timestamp);
		long ourDelay = difference - minDelay.getMinDelay();
		logger.minDelay(minDelay.getMinDelay());
		logger.ourDelay(ourDelay);
		long offTarget = C_CONTROL_TARGET_MS - ourDelay;
		logger.offTarget(offTarget);
		double delayFactor = ((double) offTarget) / ((double) C_CONTROL_TARGET_MS);
		logger.delayFactor(delayFactor);
		double windowFactor = (Math.min((double)packetSizeJustAcked, (double)maxWindow)) / (Math.max((double)maxWindow, (double)packetSizeJustAcked));
		logger.windowFactor(windowFactor);
		int gain = (int) (MAX_CWND_INCREASE_PACKETS_PER_RTT * delayFactor * windowFactor);
		logger.gain(gain);
		maxWindow += gain;
		if (maxWindow < 0) {
			maxWindow = MAX_PACKET_SIZE;
		}
		logger.maxWindow(maxWindow);
		logger.advertisedWindow(advertisedWindowSize);
		buffer.setTimeOutMicroSec(getTimeOutMicros());
	}


	private long getTimeOutMicros() {
		return Math.max(rtt*1000 + rttVar * 4 * 1000, MINIMUM_TIMEOUT_MILLIS*1000);
//		return rtt*1000 + rttVar * 4 * 1000;
	}


	private void updateMinDelay(long difference, long timestamp) {
		minDelay.updateMinDelay(difference, timestamp);
	}

	public Queue<DatagramPacket> getPacketsToResend() throws SocketException {
		Queue<DatagramPacket> queue = new LinkedList<DatagramPacket>();
		Queue<UtpTimestampedPacketDTO> toResend = buffer.getPacketsToResend();
		for (UtpTimestampedPacketDTO utpTimestampedPacketDTO : toResend) {
			queue.add(utpTimestampedPacketDTO.dataGram());			
			if (utpTimestampedPacketDTO.reduceWindow()) {
				if (reduceWindowNecessary()) {
					lastTimeWindowReduced = timeStamper.timeStamp();
					maxWindow *= 0.5;					
				}
				utpTimestampedPacketDTO.setReduceWindow(false);
			} else {
				utpTimestampedPacketDTO.setReduceWindow(true);
			}
		}
		return queue;
	}


	private boolean reduceWindowNecessary() {
		if (lastTimeWindowReduced == 0) {
			return true;
		} 
		
		long now = timeStamper.timeStamp();
		long delta = now - lastTimeWindowReduced;
		return delta > rtt*1000;
		
	}


	private SelectiveAckHeaderExtension findSelectiveAckExtension(
			UtpPacket utpPacket) {
		UtpHeaderExtension[] extensions = utpPacket.getExtensions();
 		for (int i = 0; i < extensions.length; i++) {
 			if (extensions[i] instanceof SelectiveAckHeaderExtension) {
 				return (SelectiveAckHeaderExtension) extensions[i];
 			}
 		}
 		return null;
	}

	
	
	public boolean canSendNextPacket() {
		checkTimeouts();
		int maximumWindow = (advertisedWindowSize < maxWindow) ? advertisedWindowSize : maxWindow;
		boolean canSend = maximumWindow >= currentWindow;
		boolean burstFull = false;
		
		if (canSend) {
			burstFull = (currentBurstSend >= MAX_BURST_SEND);
		}
		
		if (!burstFull && canSend) {
			currentBurstSend++;
		} 
		
		if (burstFull) {
			currentBurstSend = 0;
		}
		return SEND_IN_BURST ? (!burstFull && canSend) : canSend;
	}
	

	public int sizeOfNextPacket() {
		if (packetSizeModus.equals(PacketSizeModus.DYNAMIC_LINEAR)) {
			int packetSizeDelta = MAX_PACKET_SIZE - MIN_PACKET_SIZE;
			double packetSizeFactor = (((double) C_CONTROL_TARGET_MS) - ((double) minDelay.getMinDelay()))/((double) C_CONTROL_TARGET_MS);
			double packetSize = MIN_PACKET_SIZE + packetSizeFactor*packetSizeDelta;
			return (int) Math.ceil(packetSize);
		} else if (packetSizeModus.equals(PacketSizeModus.CONSTANT_1472)) {
			return MAX_PACKET_SIZE;
		} else {
			return MINIMUM_MTU;
		}
	}

	public void markPacketOnfly(UtpPacket utpPacket, DatagramPacket dgPacket) {
		long timeStamp = timeStamper.timeStamp();
		UtpTimestampedPacketDTO pkt = new UtpTimestampedPacketDTO(dgPacket, utpPacket, timeStamp, 0);
		buffer.bufferPacket(pkt);
		incrementAckNumber();
		currentWindow += UtpPacketUtils.DEF_HEADER_LENGTH;
		if (utpPacket.getPayload() != null) {
			currentWindow += utpPacket.getPayload().length;
		}

	}

	private void incrementAckNumber() {
		if (currentAckPosition == UnsignedTypesUtil.MAX_USHORT) {
			currentAckPosition = 1;
		} else {
			currentAckPosition++;
		}
		
	}

	public void markFinOnfly(UtpPacket fin) {
		long timeStamp = timeStamper.timeStamp();
		byte[] finBytes = fin.toByteArray();
		DatagramPacket dgFin = new DatagramPacket(finBytes, finBytes.length);
		UtpTimestampedPacketDTO pkt = new UtpTimestampedPacketDTO(dgFin, fin, timeStamp, 0);
		buffer.bufferPacket(pkt);
		incrementAckNumber();
		currentWindow += UtpPacketUtils.DEF_HEADER_LENGTH;
		if (fin.getPayload() != null) {
			currentWindow += fin.getPayload().length;
		}
	}

	public boolean areAllPacketsAcked() {
		return buffer.isEmpty();
	}

	public MinimumDelay getMinDelay() {
		return minDelay;
	}
	
	public void setMinDelay(MinimumDelay minDelay) {
		this.minDelay = minDelay;
	}

	public void setTimeStamper(MicroSecondsTimeStamp timeStamper) {
		this.timeStamper = timeStamper;
		
	}
	
	public void initiateAckPosition(int sequenceNumber) {
		if (sequenceNumber == 0) {
			throw new IllegalArgumentException("sequence number cannot be 0");
		}
		if (sequenceNumber == 1) {
			currentAckPosition = (int) UnsignedTypesUtil.MAX_USHORT;
		} else {
			currentAckPosition = sequenceNumber - 1;
		}
		
	}
	
	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @return String binary representation. 
	 */
	private static String toBinaryString(int value, int length) {
		String result = Integer.toBinaryString(value);
		
		StringBuilder buf = new StringBuilder();
		for (int i = 0; (i + result.length()) < length; i++) {
			buf.append("0");
		}
		buf.append(result);
		return buf.toString();
	}
	
	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @return String binary representation. 
	 */
	public static String toBinaryString(byte value) {
		return toBinaryString((value & 0xFF), 8);
	}

	public void removeAcked() {
		buffer.removeAcked();	
		currentWindow = buffer.getBytesOnfly();
	}


	public String getLeftElements() {
		return buffer.getSequenceOfLeft();
	}


	public long getMicrosToNextTimeOut() {
		long oldestTimeStamp = buffer.getOldestUnackedTimestamp();
		long nextTimeOut = oldestTimeStamp + getTimeOutMicros();
		long timeOutInMicroSeconds = nextTimeOut - timeStamper.timeStamp();
		if (timeOutInMicroSeconds < 0 || oldestTimeStamp == 0) {
			return 0L;
		} else {
			return timeOutInMicroSeconds + 1;
		}
	}


	public void end() {
		logger.end();
	}
	
	public void resetBurst() {
		currentBurstSend = 0;
	}
	
}
