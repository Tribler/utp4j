package ch.uzh.csg.utp4j.channels.impl.alg;

import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import ch.uzh.csg.utp4j.channels.impl.UtpTimestampedPacketDTO;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;
import ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil;

/*Logging*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutPacketBuffer {
	
	private static int size = 3000;
	private ArrayList<UtpTimestampedPacketDTO> buffer = new ArrayList<UtpTimestampedPacketDTO>(size);
	private int bytesOnFly = 0;
	private long resendTimeOutMicros;
	
	private final static Logger log = LoggerFactory.getLogger(OutPacketBuffer.class);
	
	public long getResendTimeOutMicros() {
		return resendTimeOutMicros;
	}


	public void setResendtimeOutMicros(long timeOutMicroSec) {
		this.resendTimeOutMicros = timeOutMicroSec;
	}

	private MicroSecondsTimeStamp timeStamper;
	private SocketAddress addr;
	
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

	public int markPacketAcked(int seqNrToAck, long timestamp) {
		int bytesJustAcked = -1;
		UtpTimestampedPacketDTO pkt =  findPacket(seqNrToAck);
		if (pkt != null) {
			if ((pkt.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
				if (!pkt.isPacketAcked()) {
					int payloadLength = pkt.utpPacket().getPayload() == null ? 0 : pkt.utpPacket().getPayload().length;
					bytesJustAcked = payloadLength + UtpPacketUtils.DEF_HEADER_LENGTH;					
				}
				pkt.setPacketAcked(true);
				for (UtpTimestampedPacketDTO toAck : buffer) {
					if ((toAck.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
						break;
					} else {
						toAck.setPacketAcked(true);
					}
				}
			} else {
				System.err.println("ERROR FOUND WRONG SEQ NR: " + seqNrToAck + " but returned " + (pkt.utpPacket().getSequenceNumber() & 0xFFFF));
			}
		} 
		return bytesJustAcked;
	}

	private UtpTimestampedPacketDTO findPacket(int seqNrToAck) {
		
		if (!buffer.isEmpty()) {
			int firstSeqNr = buffer.get(0).utpPacket().getSequenceNumber() & 0xFFFF;
			int index = seqNrToAck - firstSeqNr;
			if (index < 0) {
				// overflow in seq nr
				index += UnsignedTypesUtil.MAX_USHORT;
			}
			// bug
//			if (index >= buffer.size()) {
//				return null;
//			}

			if (index < buffer.size() && (buffer.get(index).utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
				return buffer.get(index);
			} else {
				// bug -> search sequentially until fixed
				for (int i = 0; i < buffer.size(); i++) {
					UtpTimestampedPacketDTO pkt = buffer.get(i);
					if ((pkt.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
						return pkt;
					}
				}
			}
			return null;
		}
		
		return null;
		
//		for (UtpTimestampedPacketDTO pkt : buffer) {
//			if ((pkt.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
//				return pkt;
//			}
//		}
//		return null;
	}



	public void removeAcked() {
		ArrayList<UtpTimestampedPacketDTO> toRemove = new ArrayList<UtpTimestampedPacketDTO>(size);
		for (UtpTimestampedPacketDTO pkt : buffer) {
			if (pkt.isPacketAcked()) {
				//we got the header, remove it from the bytes that are on the wire
				bytesOnFly -= UtpPacketUtils.DEF_HEADER_LENGTH;
				if (pkt.utpPacket().getPayload() != null) {
					//in case of a data packet, subtract the payload
					bytesOnFly -= pkt.utpPacket().getPayload().length;
				}
				toRemove.add(pkt);
			} else {
				break;
			}
		}
		buffer.removeAll(toRemove);
	}

	public Queue<UtpTimestampedPacketDTO> getPacketsToResend(int maxResend) throws SocketException {
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
			if (resendRequired(unackedPkt) && toReturn.size() <= maxResend) {
				toReturn.add(unackedPkt);
				updateResendTimeStamps(unackedPkt);
			}
			unackedPkt.setAckedAfterMeCounter(0);
		}
		
		return toReturn;

	}

	private void updateResendTimeStamps(UtpTimestampedPacketDTO unackedPkt) throws SocketException {
		unackedPkt.utpPacket().setTimestamp(timeStamper.utpTimeStamp());
		byte[] newBytes = unackedPkt.utpPacket().toByteArray();
		//TB: why create new datagram packet, can't it be reused?
		unackedPkt.setDgPacket(new DatagramPacket(newBytes, newBytes.length, addr));
		long timeStamp = timeStamper.timeStamp();
		unackedPkt.setStamp(timeStamp);
	}


	private boolean resendRequired(UtpTimestampedPacketDTO unackedPkt) {
		boolean fastResend = false;
		if (unackedPkt.getAckedAfterMeCounter() >= UtpAlgConfiguration.MIN_SKIP_PACKET_BEFORE_RESEND) {
			if (!unackedPkt.alreadyResendBecauseSkipped()) {
				fastResend = true;
				unackedPkt.setResendBecauseSkipped(true);
			}
		}
		boolean timedOut = isTimedOut(unackedPkt);
		
		if (!timedOut && fastResend) {
			unackedPkt.setReduceWindow(false);
		}
		if (timedOut && !unackedPkt.reduceWindow()) {
			unackedPkt.setReduceWindow(true);
		}
		
		return fastResend || timedOut;
	}


	public int getBytesOnfly() {
		return bytesOnFly;
	}
	
	private boolean isTimedOut(UtpTimestampedPacketDTO utpTimestampedPacketDTO) {
		long currentTimestamp = timeStamper.timeStamp();
		long delta = currentTimestamp - utpTimestampedPacketDTO.stamp();
//		if (delta > timeOutMicroSec) {
//			log.debug("timed out so resending: " + (utpTimestampedPacketDTO.utpPacket().getSequenceNumber() & 0xFFFF));
//		}
		return delta > resendTimeOutMicros;
	}


	public String getSequenceOfLeft() {
		String returnString = "";
		for (UtpTimestampedPacketDTO el : buffer) {
			returnString += " " + (el.utpPacket().getSequenceNumber() & 0xFFFF);
		}
		return returnString;
	}


	public long getOldestUnackedTimestamp() {
		if (!buffer.isEmpty()) {
			long timeStamp = Long.MAX_VALUE;
			for (UtpTimestampedPacketDTO pkt : buffer) {
				if (pkt.stamp() < timeStamp && !pkt.isPacketAcked()) {
					timeStamp = pkt.stamp();
				}
			}
			return timeStamp;
		} else return 0L;
	}


	public long getSendTimeStamp(int seqNrToAck) {
		UtpTimestampedPacketDTO pkt = findPacket(seqNrToAck);
		if (pkt != null) {
			return pkt.stamp();
		}
		return -1;
	}


	public void setRemoteAdress(SocketAddress addr) {
		this.addr = addr;
		
	}


	public int getResendCounter(int seqNrToAck) {
		UtpTimestampedPacketDTO pkt = findPacket(seqNrToAck);
		if (pkt != null) {
			return pkt.getResendCounter();
		}
		return 1;
	}

}
