package ch.uzh.csg.utp4j.data;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUbyte;

import java.net.DatagramPacket;


/**
 * Helper methods for uTP Headers.
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public class UtpPacketUtils {
	
	public static final byte VERSION = longToUbyte(16);
	public static final byte ST_DATA = (byte) (VERSION | longToUbyte(0));
	public static final byte ST_FIN = (byte) (VERSION | longToUbyte(1));
	public static final byte ST_STATE = (byte) (VERSION | longToUbyte(2));
	public static final byte ST_RESET = (byte) (VERSION | longToUbyte(3));
	public static final byte ST_SYN = (byte) (VERSION | longToUbyte(4));
	
	public static final byte NO_EXTENSION = longToUbyte(0);
	public static final byte SELECTIVE_ACK = longToUbyte(1);
	
	public static final int MAX_UTP_PACKET_LENGTH = 1500;
	public static final int MAX_UDP_HEADER_LENGTH = 48;
	public static final int DEF_HEADER_LENGTH = 20;
	
	public static byte[] joinByteArray(byte[] array1, byte[] array2) {
		
		int length1 = array1 == null ? 0 : array1.length;
		int length2 = array2 == null ? 0 : array2.length;

		
		int totalLength = length1 + length2;
		byte[] returnArray = new byte[totalLength];
		
		int i = 0;
		for (; i < length1; i++) {
			returnArray[i] = array1[i];
		}
		
		for (int j = 0; j < length2; j++) {
			returnArray[i] = array2[j];
			i++;
		}
		
		return returnArray;
		
	}
	
	/**
	 * Creates an Utp-Packet to initialize a connection
	 * Following values will be set:
	 * <ul>
	 * <li>Type and Version</li>
	 * <li>Sequence Number</li>
	 * </ul>
	 * @return {@link UtpPacket}
	 */
	public static UtpPacket createSynPacket() {
		
		UtpPacket pkt = new UtpPacket();
		pkt.setTypeVersion(ST_SYN);
		pkt.setSequenceNumber(longToUbyte(1));
		byte[] pl = { 1, 2, 3, 4, 5, 6};
		pkt.setPayload(pl);
		return pkt;
	}
	
	public static UtpPacket extractUtpPacket(DatagramPacket dgpkt) {
		
		UtpPacket pkt = new UtpPacket();
		byte[] pktb = dgpkt.getData();
		pkt.setFromByteArray(pktb, dgpkt.getLength(), dgpkt.getOffset());
		return pkt;
	}
	
	public static boolean isSynPkt(UtpPacket packet) {
		
		if (packet == null) {
			return false;
		}
		
		return packet.getTypeVersion() == ST_SYN;
		
	}
	
	public static boolean isSynPkt(DatagramPacket packet) {
		
		if (packet == null) {
			return false;
		}
		
		byte[] data = packet.getData();
		
		if (data != null && data.length >= DEF_HEADER_LENGTH) {
			return data[0] == ST_SYN;
		}
		
		return false;
	}

}
