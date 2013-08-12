package ch.uzh.csg.utp4j.channels.impl.alg;

import java.net.DatagramPacket;
import java.util.LinkedList;
import java.util.Queue;


import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
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
	
	public static int MINIMUM_TIMEOUT_MILLIS = 100;
	
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
	public static boolean SEND_IN_BURST = false;
	
	/**
	 * Reduce burst sending artificially
	 */
	public static int MAX_BURST_SEND = 5;
	
	/**
	 * Minimum number of acks past seqNr=x to trigger a resend of seqNr=x;
	 */
	public static final int MIN_SKIP_PACKET_BEFORE_RESEND = 3;
	
	private int durchgang = 0;
	
	
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

	private int lastSequenceWithSelectiveAck;

	private byte[] lastSelectiveAckBitMask;

	private long timeOutMicroSec = MINIMUM_TIMEOUT_MILLIS * 1000;

	private int advertisedWindowSize;
	
	public UtpAlgorithm(MicroSecondsTimeStamp timestamper) {
		timeStamper = timestamper;
		buffer = new OutPacketBuffer(timestamper);
	}


	public void ackRecieved(UtpTimestampedPacketDTO pair) {
		int seqNrToAck = pair.utpPacket().getAckNumber() & 0xFFFF;
		long timestamp = timeStamper.timeStamp();
		int advertisedWindo = pair.utpPacket().getWindowSize() & 0xFFFFFFFF;
		updateAdvertisedWindowSize(advertisedWindo);
		if (buffer.markPacketAcked(seqNrToAck, timestamp)) {
			updateTimeoutCounter(timestamp);
			updateWindow(pair.utpPacket(), timestamp);
		} 
		int originalAck = seqNrToAck;
		if (pair.utpPacket().getExtensions() != null) {
			SelectiveAckHeaderExtension selAck = findSelectiveAckExtension(pair.utpPacket());
			byte[] bitMask = selAck.getBitMask();
			if (originalAck == lastSequenceWithSelectiveAck) {
				updateBitMask(bitMask);
			}
			for (int i = 0; i < bitMask.length; i++) {
				for (int j = 2; j < 9; j++) {
					if (SelectiveAckHeaderExtension.isBitMarked(bitMask[i], j)) {
						timestamp = timeStamper.timeStamp();
						buffer.markPacketAcked(i*8 + j + seqNrToAck, timestamp);							
					}
				}
			}
			lastSelectiveAckBitMask = bitMask;
			lastSequenceWithSelectiveAck = seqNrToAck;
		}
		
	}
		
	private void updateAdvertisedWindowSize(int advertisedWindo) {
//		this.advertisedWindowSize = advertisedWindo;
		
	}

	private void updateBitMask(byte[] bitMask) {
		for (int i = 0; i < lastSelectiveAckBitMask.length; i++) {
			bitMask[i] = (byte) ((bitMask[i] ^ lastSelectiveAckBitMask[i]) & 0xFF);

		}
		
	}

	private void checkTimeouts() {
		// TODO: check if packets timed out
	}

	private void updateTimeoutCounter(long timestamp) {
		// TODO: keep track of supposed rtt
	}
	
	private void updateWindow(UtpPacket utpPacket, long timestamp) {
		currentWindow = buffer.getBytesOnfly();
		long difference = utpPacket.getTimestampDifference() & 0xFFFFFFFF;
		updateMinDelay(difference, timestamp);
		long ourDelay = difference - minDelay.getMinDelay();
		long offTarget = C_CONTROL_TARGET_MS - ourDelay;
		double delayFactor = ((double) offTarget) / ((double) C_CONTROL_TARGET_MS);

		double windowFactor = ((double) currentWindow) / ((double) maxWindow);
		int gain = (int) (MAX_CWND_INCREASE_PACKETS_PER_RTT * delayFactor * windowFactor);
		maxWindow += gain;
		if (maxWindow < 0) {
			maxWindow = MAX_PACKET_SIZE;
		}
		if (maxWindow > 2000000) {
			maxWindow = 2000000;
		}
		buffer.setTimeOutMicroSec(timeOutMicroSec);
//		System.out.println("difference: " + difference + " our delay: " + ourDelay + " offtarget: " + offTarget);
//		System.out.println("windowfactor: " + windowFactor + " delayFactor: " + delayFactor + " gain: " + gain);
	}


	private void updateMinDelay(long difference, long timestamp) {
		minDelay.updateMinDelay(difference, timestamp);
	}

	public Queue<DatagramPacket> getPacketsToResend() {
		Queue<DatagramPacket> queue = new LinkedList<DatagramPacket>();
		Queue<UtpTimestampedPacketDTO> toResend = buffer.getPacketsToResend();
		for (UtpTimestampedPacketDTO utpTimestampedPacketDTO : toResend) {
			queue.add(utpTimestampedPacketDTO.dataGram());
			utpTimestampedPacketDTO.setStamp(timeStamper.timeStamp());
			
			if (utpTimestampedPacketDTO.reduceWindow()) {
				maxWindow *= 0.5;
				utpTimestampedPacketDTO.setReduceWindow(false);
			} else {
				utpTimestampedPacketDTO.setReduceWindow(true);
			}

		}

		return queue;
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
		if (durchgang % 1000 == 0) {
			System.out.println("MAX:CURR " + maximumWindow + " " + currentWindow);
		}
		durchgang++;
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
	
}
