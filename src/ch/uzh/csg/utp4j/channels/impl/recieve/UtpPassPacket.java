package ch.uzh.csg.utp4j.channels.impl.recieve;

import java.net.DatagramPacket;

public class UtpPassPacket extends Thread implements Runnable {

	private UtpPacketRecievable reciever;
	private DatagramPacket packet;
	
	public UtpPassPacket(DatagramPacket packet, UtpPacketRecievable reciever) {
		this.packet = packet;
		this.reciever = reciever;
	}
	
	@Override
	public void run() {
		reciever.recievePacket(packet);
	}
}
