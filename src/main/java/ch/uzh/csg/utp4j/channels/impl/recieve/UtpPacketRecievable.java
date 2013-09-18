package ch.uzh.csg.utp4j.channels.impl.recieve;

import java.net.DatagramPacket;

public interface UtpPacketRecievable {
	
	public void recievePacket(DatagramPacket packet);

}
