package ch.uzh.csg.utp4j.channels.impl.alg;

import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.channels.impl.log.UtpDataLogger;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.SelectiveAckHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpHeaderExtension;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;
import ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil;
import static ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration.*;

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

	private long rtt;
	private long rttVar = 0;
	

	private int advertisedWindowSize;
	private boolean advertisedWindowSizeSet = false;

	private UtpDataLogger logger = new UtpDataLogger();

	private long lastTimeWindowReduced;
	private long timeStampNow;
	private long lastAckRecieved;
	
	private final static Logger log = LoggerFactory.getLogger(UtpAlgorithm.class);

	
	public UtpAlgorithm(MicroSecondsTimeStamp timestamper, SocketAddress addr) {
		maxWindow = MAX_CWND_INCREASE_PACKETS_PER_RTT;
		rtt = MINIMUM_TIMEOUT_MILLIS*2;
		timeStamper = timestamper;
		buffer = new OutPacketBuffer(timestamper);
		buffer.setRemoteAdress(addr);
		log.debug(UtpAlgConfiguration.getString());
		timeStampNow = timeStamper.timeStamp();
	}


	public void ackRecieved(UtpTimestampedPacketDTO pair) {
		int seqNrToAck = pair.utpPacket().getAckNumber() & 0xFFFF;
		timeStampNow = timeStamper.timeStamp();
		lastAckRecieved = timeStampNow;
		int advertisedWindo = pair.utpPacket().getWindowSize() & 0xFFFFFFFF;
		updateAdvertisedWindowSize(advertisedWindo);
		logger.ackRecieved(seqNrToAck);
		int packetSizeJustAcked = buffer.markPacketAcked(seqNrToAck, timeStampNow);
		if (packetSizeJustAcked > 0) {
			updateRtt(timeStampNow, seqNrToAck);
			updateWindow(pair.utpPacket(), timeStampNow, packetSizeJustAcked, pair.utpTimeStamp());
		} 

		if (pair.utpPacket().getExtensions() != null) {
			SelectiveAckHeaderExtension selAck = findSelectiveAckExtension(pair.utpPacket());
			byte[] bitMask = selAck.getBitMask();
			for (int i = 0; i < bitMask.length; i++) {
				for (int j = 2; j < 9; j++) {
					if (SelectiveAckHeaderExtension.isBitMarked(bitMask[i], j)) {
						int sackSeqNr = i*8 + j + seqNrToAck;
						logger.sAck(sackSeqNr);
						packetSizeJustAcked = buffer.markPacketAcked(sackSeqNr, timeStampNow);
						if (packetSizeJustAcked > 0) {
							updateRtt(timeStampNow, sackSeqNr);
							updateWindow(pair.utpPacket(), timeStampNow, packetSizeJustAcked, pair.utpTimeStamp());
						} 
					}
				}
			}
		}
		logger.next();
		
	}
		
	private void updateRtt(long timestamp, int seqNrToAck) {
		long sendTimeStamp = buffer.getSendTimeStamp(seqNrToAck);	
		if (rttUpdateNecessary(sendTimeStamp, seqNrToAck)) {
			long packetRtt = (timestamp-sendTimeStamp)/1000;;
			long delta = rtt - packetRtt;
			rttVar += (Math.abs(delta) - rttVar)/4;
			rtt += (packetRtt - rtt)/8;
			logger.pktRtt(packetRtt);
			logger.rttVar(rttVar);
			logger.rtt(rtt);
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
		logger.microSecTimeStamp(timeStampNow);
		currentWindow = buffer.getBytesOnfly();
		logger.currentWindow(currentWindow);
		
		long ourDifference = utpPacket.getTimestampDifference() & 0xFFFFFFFF;
		logger.ourDifference(ourDifference);
		updateOurDelay(ourDifference);
		
		int theirDifference = timeStamper.utpDifference(utpRecieved, utpPacket.getTimestamp());
		
		logger.theirDifference(theirDifference);
		updateTheirDelay(theirDifference);
		logger.theirMinDelay(minDelay.getTheirMinDelay());
		
		long ourDelay = ourDifference - minDelay.getCorrectedMinDelay();
		logger.minDelay(minDelay.getCorrectedMinDelay());
		logger.ourDelay(ourDelay);
		
		long offTarget = C_CONTROL_TARGET_MICROS - ourDelay;
		logger.offTarget(offTarget);
		double delayFactor = ((double) offTarget) / ((double) C_CONTROL_TARGET_MICROS);
		logger.delayFactor(delayFactor);
		double windowFactor = (Math.min((double)packetSizeJustAcked, (double)maxWindow)) / (Math.max((double)maxWindow, (double)packetSizeJustAcked));
		logger.windowFactor(windowFactor);
		int gain = (int) (MAX_CWND_INCREASE_PACKETS_PER_RTT * delayFactor * windowFactor);
		
		if (ONLY_POSITIVE_GAIN && gain < 0) {
			gain = 0;
		}
		
		logger.gain(gain);
		maxWindow += gain;
		if (maxWindow < 0) {
			maxWindow = 0;
		}
		
//		log.debug("current:max " + currentWindow + ":" + maxWindow);
		logger.maxWindow(maxWindow);
		logger.advertisedWindow(advertisedWindowSize);
		buffer.setResendtimeOutMicros(getEstimatedRttMicros());
		
		if (maxWindow == 0) {
			lastZeroWindow = timeStampNow;
		}
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
			utpTimestampedPacketDTO.incrementResendCounter();
			if (utpTimestampedPacketDTO.reduceWindow()) {
				if (reduceWindowNecessary()) {
					lastTimeWindowReduced = timeStampNow;
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
		
		long delta = timeStampNow - lastTimeWindowReduced;
		return delta > getEstimatedRttMicros();
		
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
		if (timeStampNow - lastZeroWindow > getTimeOutMicros() && lastZeroWindow != 0) {
			log.debug("Reducing window");
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
		long minDelayOffTarget = C_CONTROL_TARGET_MICROS - minDelay.getCorrectedMinDelay();
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
		return timeOutInMicroSeconds < 0 || oldestTimeStamp == 0;
	}


	public void end(int bytesSend, boolean successfull) {
		if (successfull) {
			logger.end(bytesSend);			
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
	
}
