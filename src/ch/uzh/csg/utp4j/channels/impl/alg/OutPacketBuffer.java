package ch.uzh.csg.utp4j.channels.impl.alg;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

public class OutPacketBuffer {
	
	private static int size = 1000;
	private ArrayList<UtpTimestampedPacketDTO> buffer = new ArrayList<UtpTimestampedPacketDTO>(size);
	private int bytesOnFly = 0;
	private long timeOutMicroSec;
	
	public long getTimeOutMicroSec() {
		return timeOutMicroSec;
	}


	public void setTimeOutMicroSec(long timeOutMicroSec) {
		this.timeOutMicroSec = timeOutMicroSec;
	}

	private MicroSecondsTimeStamp timeStamper;
	
	public OutPacketBuffer(MicroSecondsTimeStamp stamper) {
		timeStamper = stamper;
	}
	

	public void bufferPacket(UtpTimestampedPacketDTO pkt) {
		buffer.add(pkt);
		if (pkt.utpPacket().getPayload() != null) {
			bytesOnFly += pkt.utpPacket().getPayload().length;
		}
		bytesOnFly += UtpPacketUtils.DEF_HEADER_LENGTH;
	}


	public boolean isEmpty() {
		return buffer.isEmpty();
	}

	public boolean markPacketAcked(int seqNrToAck, long timestamp) {
		boolean returnValue = false;
		UtpTimestampedPacketDTO pkt =  findPacket(seqNrToAck);
		if (pkt != null) {
			if ((pkt.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
				pkt.setPacketAcked(true);
				returnValue = true;
			}
		} else {
			returnValue = false;
		}
 		
		return returnValue;
	}

	private UtpTimestampedPacketDTO findPacket(int seqNrToAck) {
		for (UtpTimestampedPacketDTO pkt : buffer) {
			if ((pkt.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
				return pkt;
			}
		}
		return null;
	}



	public void removeAcked() {
		ArrayList<UtpTimestampedPacketDTO> toRemove = new ArrayList<UtpTimestampedPacketDTO>(size);
		for (UtpTimestampedPacketDTO pkt : buffer) {
			if (pkt.isPacketAcked()) {
				bytesOnFly -= UtpPacketUtils.DEF_HEADER_LENGTH;
				if (pkt.utpPacket().getPayload() != null) {
					bytesOnFly -= pkt.utpPacket().getPayload().length;
				}
				toRemove.add(pkt);
			} else {
				break;
			}
		}
		buffer.removeAll(toRemove);
	}

	public Queue<UtpTimestampedPacketDTO> getPacketsToResend() {
		Queue<UtpTimestampedPacketDTO> unacked = new LinkedList<UtpTimestampedPacketDTO>();
		for (UtpTimestampedPacketDTO pkt : buffer) {
			if (!pkt.isPacketAcked()) {
				unacked.add(pkt);
			} else {
				for (UtpTimestampedPacketDTO unackedPkt : unacked) {
					unackedPkt.incrementAckedAfterMe();
				}
			}
		}
		Queue<UtpTimestampedPacketDTO> toReturn = new LinkedList<UtpTimestampedPacketDTO>();
		for (UtpTimestampedPacketDTO unackedPkt : unacked) {
			if (resendRequired(unackedPkt)) {
				toReturn.add(unackedPkt);
				if (!unackedPkt.reduceWindow()) {
					unackedPkt.setReduceWindow(true);
				}
			}
			unackedPkt.setAckedAfterMeCounter(0);
		}
		
		return toReturn;

	}

	private boolean resendRequired(UtpTimestampedPacketDTO unackedPkt) {
		boolean resend = false;
		if (unackedPkt.getAckedAfterMeCounter() >= UtpAlgorithm.MIN_SKIP_PACKET_BEFORE_RESEND) {
			if (!unackedPkt.alreadyResendBecauseSkipped()) {
				resend = true;
				unackedPkt.setResendBecauseSkipped(true);
			}
		}
		resend = resend || isTimedOut(unackedPkt);
		return resend;
	}


	public int getBytesOnfly() {
		return bytesOnFly;
	}
	
	private boolean isTimedOut(UtpTimestampedPacketDTO utpTimestampedPacketDTO) {
		long currentTimestamp = timeStamper.timeStamp();
		long delta = currentTimestamp - utpTimestampedPacketDTO.stamp();
		if ((delta > timeOutMicroSec)) {
		}
		return delta > timeOutMicroSec;
	}

}
