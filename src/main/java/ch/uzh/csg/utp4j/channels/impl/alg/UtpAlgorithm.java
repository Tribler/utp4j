package ch.uzh.csg.utp4j.channels.impl.alg;

import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.C_CONTROL_TARGET_MICROS;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.MAX_BURST_SEND;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.MAX_CWND_INCREASE_PACKETS_PER_RTT;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.MAX_PACKET_SIZE;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.MICROSECOND_WAIT_BETWEEN_BURSTS;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.MINIMUM_MTU;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.MINIMUM_TIMEOUT_MILLIS;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.MIN_PACKET_SIZE;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.ONLY_POSITIVE_GAIN;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.PACKET_SIZE_MODE;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.SEND_IN_BURST;

import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.channels.impl.log.UtpDataLogger;
import ch.uzh.csg.utp4j.channels.impl.log.UtpNopLogger;
import ch.uzh.csg.utp4j.channels.impl.log.UtpStatisticLogger;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.SelectiveAckHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;
import ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil;

public class UtpAlgorithm {

	/**
	 * Variables
	 */
	private int currentWindow = 0;
	private int maxWindow;
	private MinimumDelay minDelay = new MinimumDelay();
	private OutPacketBuffer buffer;
	private MicroSecondsTimeStamp timeStamper;
	private int currentAckPosition = 0;
	private int currentBurstSend = 0;
	private long lastZeroWindow;
	private ByteBuffer bBuffer;

	private long rtt;
	private long rttVar = 0;
	

	private int advertisedWindowSize;
	private boolean advertisedWindowSizeSet = false;

	private UtpStatisticLogger statisticLogger;

	private long lastTimeWindowReduced;
	private long timeStampNow;
	private long lastAckRecieved;
	
	private int resendedPackets = 0;
	private int totalPackets = 0;
	private long lastMaxedOutWindow;
	
	private final static Logger log = LoggerFactory.getLogger(UtpAlgorithm.class);

	
	public UtpAlgorithm(MicroSecondsTimeStamp timestamper, SocketAddress addr) {
		maxWindow = MAX_CWND_INCREASE_PACKETS_PER_RTT;
		rtt = MINIMUM_TIMEOUT_MILLIS*2;
		timeStamper = timestamper;
		buffer = new OutPacketBuffer(timestamper);
		buffer.setRemoteAdress(addr);
		log.debug(UtpAlgConfiguration.getString());
		timeStampNow = timeStamper.timeStamp();
		if (UtpAlgConfiguration.DEBUG) {
			statisticLogger = new UtpDataLogger();
		} else {
			statisticLogger = new UtpNopLogger();
		}
	}


	public void ackRecieved(UtpTimestampedPacketDTO pair) {
		int seqNrToAck = pair.utpPacket().getAckNumber() & 0xFFFF;
//		log.debug("Recieved ACK " + pair.utpPacket().toString());
		timeStampNow = timeStamper.timeStamp();
		lastAckRecieved = timeStampNow;
		int advertisedWindo = pair.utpPacket().getWindowSize() & 0xFFFFFFFF;
		updateAdvertisedWindowSize(advertisedWindo);
		statisticLogger.ackRecieved(seqNrToAck);
		int packetSizeJustAcked = buffer.markPacketAcked(seqNrToAck, timeStampNow, 
				UtpAlgConfiguration.AUTO_ACK_SMALLER_THAN_ACK_NUMBER);
		if (packetSizeJustAcked > 0) {
			updateRtt(timeStampNow, seqNrToAck);
			updateWindow(pair.utpPacket(), timeStampNow, packetSizeJustAcked, pair.utpTimeStamp());
		} 
		// TODO: With libutp, sometimes null pointer exception -> investigate. 
//			log.debug("utpPacket With Ext: " + pair.utpPacket().toString());
		SelectiveAckHeaderExtension selectiveAckExtension = findSelectiveAckExtension(pair.utpPacket());
		if (selectiveAckExtension != null) {
			
			// if a new packed is acked by selectiveAck, we will 
			// only update this one. if more than one is acked newly,
			// ignore it, because it will corrupt our measurements
			boolean windowAlreadyUpdated = false;
			
			// For each byte in the selective Ack header extension
			byte[] bitMask = selectiveAckExtension.getBitMask();
			for (int i = 0; i < bitMask.length; i++) {
				// each bit in the extension, from 2 to 9, because least significant
				// bit is ACK+2, most significant bit is ack+9 -> loop [2,9]
				for (int j = 2; j < 10; j++) {
					if (SelectiveAckHeaderExtension.isBitMarked(bitMask[i], j)) {
						// j-th bit of i-th byte + seqNrToAck equals our selective-Ack-number.
						// example: 
						// ack:5, sack: 8 -> i = 0, j =3 -> 0*8+3+5 = 8. 
						// bitpattern in this case would be 00000010, bit_index 1 from right side, added 2 to it equals 3
						// thats why we start with j=2. most significant bit is index 7, j would be 9 then. 
						int sackSeqNr = i*8 + j + seqNrToAck;
						// sackSeqNr can overflow too !!
						if (sackSeqNr > UnsignedTypesUtil.MAX_USHORT) {
							sackSeqNr -= UnsignedTypesUtil.MAX_USHORT;
						}
						statisticLogger.sAck(sackSeqNr);
						// dont ack smaller seq numbers in case of Selective ack !!!!!
						packetSizeJustAcked = buffer.markPacketAcked(sackSeqNr, timeStampNow, false);
						if (packetSizeJustAcked > 0 && !windowAlreadyUpdated) {
							windowAlreadyUpdated = true;
							updateRtt(timeStampNow, sackSeqNr);
							updateWindow(pair.utpPacket(), timeStampNow, packetSizeJustAcked, pair.utpTimeStamp());
						} 
					}
				}
			}
		}
		statisticLogger.next();
		
	}
		
	private void updateRtt(long timestamp, int seqNrToAck) {
		long sendTimeStamp = buffer.getSendTimeStamp(seqNrToAck);	
		if (rttUpdateNecessary(sendTimeStamp, seqNrToAck)) {
			long packetRtt = (timestamp-sendTimeStamp)/1000;
			long delta = rtt - packetRtt;
			rttVar += (Math.abs(delta) - rttVar)/4;
			rtt += (packetRtt - rtt)/8;
			statisticLogger.pktRtt(packetRtt);
			statisticLogger.rttVar(rttVar);
			statisticLogger.rtt(rtt);
		}
	}


	private boolean rttUpdateNecessary(long sendTimeStamp, int seqNrToAck) {
		return sendTimeStamp != -1 && buffer.getResendCounter(seqNrToAck) == 0;
	}


	private void updateAdvertisedWindowSize(int advertisedWindo) {
		if (!advertisedWindowSizeSet) {
			advertisedWindowSizeSet = true;
		}
		this.advertisedWindowSize = advertisedWindo;
		
	}	
	private void updateWindow(UtpPacket utpPacket, long timestamp, int packetSizeJustAcked, int utpRecieved) {
		statisticLogger.microSecTimeStamp(timeStampNow);
		currentWindow = buffer.getBytesOnfly();
		
		if (isWondowFull()) {
			lastMaxedOutWindow = timeStampNow;
		}
		
		statisticLogger.currentWindow(currentWindow);
		
		long ourDifference = utpPacket.getTimestampDifference() & 0xFFFFFFFF;
		statisticLogger.ourDifference(ourDifference);
		updateOurDelay(ourDifference);
		
		int theirDifference = timeStamper.utpDifference(utpRecieved, utpPacket.getTimestamp());
		
		statisticLogger.theirDifference(theirDifference);
		updateTheirDelay(theirDifference);
		statisticLogger.theirMinDelay(minDelay.getTheirMinDelay());
		
		long ourDelay = ourDifference - minDelay.getCorrectedMinDelay();
		minDelay.addSample(ourDelay);
		statisticLogger.minDelay(minDelay.getCorrectedMinDelay());
		statisticLogger.ourDelay(ourDelay);
		
		long offTarget = C_CONTROL_TARGET_MICROS - ourDelay;
		statisticLogger.offTarget(offTarget);
		double delayFactor = ((double) offTarget) / ((double) C_CONTROL_TARGET_MICROS);
		statisticLogger.delayFactor(delayFactor);
		double windowFactor = (Math.min((double)packetSizeJustAcked, (double)maxWindow)) / (Math.max((double)maxWindow, (double)packetSizeJustAcked));
		statisticLogger.windowFactor(windowFactor);
		int gain = (int) (MAX_CWND_INCREASE_PACKETS_PER_RTT * delayFactor * windowFactor);
		
		if (setGainToZero(gain)) {
			gain = 0;
		}
		
		statisticLogger.gain(gain);
		maxWindow += gain;
		if (maxWindow < 0) {
			maxWindow = 0;
		}
		
//		log.debug("current:max " + currentWindow + ":" + maxWindow);
		statisticLogger.maxWindow(maxWindow);
		statisticLogger.advertisedWindow(advertisedWindowSize);
		buffer.setResendtimeOutMicros(getTimeOutMicros());
		
		if (maxWindow == 0) {
			lastZeroWindow = timeStampNow;
		}
		// get bytes successfully transmitted: 
		// this is the position of the bytebuffer (comes from programmer)
		// substracted by the amount of bytes on fly (these are not yet acked)
		int bytesSend = bBuffer.position() - buffer.getBytesOnfly();
		statisticLogger.bytesSend(bytesSend);
		
//		maxWindow = 10000;
	}



	private boolean setGainToZero(int gain) {
		// if i have ever reached lastMaxWindow then check if its longer than 1kk micros
		// if not, true
		boolean lastMaxWindowNeverReached 
			= (lastMaxedOutWindow != 0 
				&& (lastMaxedOutWindow - timeStampNow >= 1000000)) ||
				lastMaxedOutWindow == 0;
		if (lastMaxWindowNeverReached) {
			log.debug("last maxed window: setting gain to 0");
		}
		return (ONLY_POSITIVE_GAIN && gain < 0) || lastMaxWindowNeverReached;
	}


	private void updateTheirDelay(long theirDifference) {
		minDelay.updateTheirDelay(theirDifference, timeStampNow);		
	}


	private long getTimeOutMicros() {
		return Math.max(getEstimatedRttMicros(), MINIMUM_TIMEOUT_MILLIS*1000);
	}
	
	private long getEstimatedRttMicros() {
		return rtt*1000 + rttVar * 4 * 1000;
	}


	private void updateOurDelay(long difference) {
		minDelay.updateOurDelay(difference, timeStampNow);
	}

	public Queue<DatagramPacket> getPacketsToResend() throws SocketException {
		timeStampNow = timeStamper.timeStamp();
		Queue<DatagramPacket> queue = new LinkedList<DatagramPacket>();
		Queue<UtpTimestampedPacketDTO> toResend = buffer.getPacketsToResend(UtpAlgConfiguration.MAX_BURST_SEND);
		for (UtpTimestampedPacketDTO utpTimestampedPacketDTO : toResend) {
			queue.add(utpTimestampedPacketDTO.dataGram());
//			log.debug("Resending: " + utpTimestampedPacketDTO.utpPacket().toString() );
			utpTimestampedPacketDTO.incrementResendCounter();
			if (utpTimestampedPacketDTO.reduceWindow()) {
				if (reduceWindowNecessary()) {
					lastTimeWindowReduced = timeStampNow;
					maxWindow *= 0.5;					
				}
				utpTimestampedPacketDTO.setReduceWindow(false);
			}
		}
		resendedPackets += queue.size();
		return queue;
	}


	private boolean reduceWindowNecessary() {
		if (lastTimeWindowReduced == 0) {
			return true;
		} 
		
		long delta = timeStampNow - lastTimeWindowReduced;
		return delta > getEstimatedRttMicros();
		
	}


	private SelectiveAckHeaderExtension findSelectiveAckExtension(
			UtpPacket utpPacket) {
		UtpHeaderExtension[] extensions = utpPacket.getExtensions();
		if (extensions == null) {
			return null;
		}
 		for (int i = 0; i < extensions.length; i++) {
 			if (extensions[i] instanceof SelectiveAckHeaderExtension) {
 				return (SelectiveAckHeaderExtension) extensions[i];
 			}
 		}
 		return null;
	}

	
	
	public boolean canSendNextPacket() {
		if (timeStampNow - lastZeroWindow > getTimeOutMicros() && lastZeroWindow != 0 && maxWindow == 0) {
			log.debug("setting window to one packet size. current window is:" + currentWindow);
			maxWindow = MAX_PACKET_SIZE;
		}
		boolean windowNotFull = !isWondowFull();
		boolean burstFull = false;
		
		if (windowNotFull) {
			burstFull = isBurstFull();
		}
		
		if (!burstFull && windowNotFull) {
			currentBurstSend++;
		} 
		
		if (burstFull) {
			currentBurstSend = 0;
		}
		return SEND_IN_BURST ? (!burstFull && windowNotFull) : windowNotFull;
	}
	
	private boolean isBurstFull() {
		return currentBurstSend >= MAX_BURST_SEND;
	}
	

	private boolean isWondowFull() {
		int maximumWindow = (advertisedWindowSize < maxWindow 
				&& advertisedWindowSizeSet) ? advertisedWindowSize : maxWindow;
		return currentWindow >= maximumWindow;
	}
	
	public int sizeOfNextPacket() {
		if (PACKET_SIZE_MODE.equals(PacketSizeModus.DYNAMIC_LINEAR)) {
			return calculateDynamicLinearPacketSize();
		} else if (PACKET_SIZE_MODE.equals(PacketSizeModus.CONSTANT_1472)) {
			return MAX_PACKET_SIZE- UtpPacketUtils.DEF_HEADER_LENGTH;
		}
		return MINIMUM_MTU - UtpPacketUtils.DEF_HEADER_LENGTH;
	}

	private int calculateDynamicLinearPacketSize() {
		int packetSizeDelta = MAX_PACKET_SIZE - MIN_PACKET_SIZE;
		long minDelayOffTarget = C_CONTROL_TARGET_MICROS - minDelay.getRecentAverageDelay();;
		minDelayOffTarget = minDelayOffTarget < 0 ? 0 : minDelayOffTarget;
		double packetSizeFactor = ((double) minDelayOffTarget)/((double) C_CONTROL_TARGET_MICROS);
		double packetSize = MIN_PACKET_SIZE + packetSizeFactor*packetSizeDelta;
		return (int) Math.ceil(packetSize);
	}


	public void markPacketOnfly(UtpPacket utpPacket, DatagramPacket dgPacket) {
		timeStampNow = timeStamper.timeStamp();
		UtpTimestampedPacketDTO pkt = new UtpTimestampedPacketDTO(dgPacket, utpPacket, timeStampNow, 0);
		buffer.bufferPacket(pkt);
		incrementAckNumber();
		addPacketToCurrentWindow(utpPacket);
		totalPackets++;

	}

	private void incrementAckNumber() {
		if (currentAckPosition == UnsignedTypesUtil.MAX_USHORT) {
			currentAckPosition = 1;
		} else {
			currentAckPosition++;
		}
		
	}

	public void markFinOnfly(UtpPacket fin) {
		timeStampNow = timeStamper.timeStamp();
		byte[] finBytes = fin.toByteArray();
		DatagramPacket dgFin = new DatagramPacket(finBytes, finBytes.length);
		UtpTimestampedPacketDTO pkt = new UtpTimestampedPacketDTO(dgFin, fin, timeStampNow, 0);
		buffer.bufferPacket(pkt);
		incrementAckNumber();
		addPacketToCurrentWindow(fin);
	}

	private void addPacketToCurrentWindow(UtpPacket pkt) {
		currentWindow += UtpPacketUtils.DEF_HEADER_LENGTH;
		if (pkt.getPayload() != null) {
			currentWindow += pkt.getPayload().length;
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
			buf.append('0');
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


	public long getWaitingTimeMicroSeconds() {
		long oldestTimeStamp = buffer.getOldestUnackedTimestamp();
		long nextTimeOut = oldestTimeStamp + getTimeOutMicros();
		timeStampNow = timeStamper.timeStamp();
		long timeOutInMicroSeconds = nextTimeOut - timeStampNow;
		if (continueImmidiately(timeOutInMicroSeconds, oldestTimeStamp)) {
			return 0L;
		}
		if (!isWondowFull()) {
			return MICROSECOND_WAIT_BETWEEN_BURSTS;
		} 
		return timeOutInMicroSeconds;
	}


	private boolean continueImmidiately(
			long timeOutInMicroSeconds, long oldestTimeStamp) {
		return timeOutInMicroSeconds < 0 || (oldestTimeStamp == 0 && maxWindow > 0);
	}


	public void end(int bytesSend, boolean successfull) {
		if (successfull) {
			statisticLogger.end(bytesSend);
			log.debug("Total packets send: " + totalPackets + ", Total Packets Resend: " + resendedPackets);
		}
	}
	
	public void resetBurst() {
		currentBurstSend = 0;
	}


	public boolean isTimedOut() {
		if (timeStampNow - lastAckRecieved > getTimeOutMicros()*5 && lastAckRecieved != 0) {
			log.debug("Timed out!");
			return true;
		}
		return false;
	}


	public void setMaxWindow(int window) {
		this.maxWindow = window;
	}


	public int getMaxWindow() {
		return maxWindow;
	}


	public int getCurrentWindow() {
		return currentWindow;
		
	}
	
	public void setByteBuffer(ByteBuffer bBuffer) {
		this.bBuffer = bBuffer;
	}
	
}
