package net.utp4j.channels.impl.recieve;

import java.net.DatagramPacket;
/**
 * All entities that can recieve UDP packets implement this (channel and server)
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public interface UtpPacketRecievable {
	/**
	 * Recieve that packet. 
	 * @param packet
	 */
    void recievePacket(DatagramPacket packet);

}
