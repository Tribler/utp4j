package ch.uzh.csg.utp4j.channels.impl.recieve;


import static ch.uzh.csg.utp4j.data.UtpPacketUtils.MAX_UDP_HEADER_LENGTH;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.MAX_UTP_PACKET_LENGTH;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;




public class UtpRecieveRunnable extends Thread implements Runnable {


	private DatagramSocket socket;
	private UtpPacketRecievable queueable;
	private boolean graceFullInterrupt = false;
	

	public UtpRecieveRunnable(DatagramSocket socket, UtpPacketRecievable queueable) {
		this.socket = socket;
		this.queueable = queueable;
	}
		
	public void graceFullInterrupt() {
		super.interrupt();
		graceFullInterrupt = true;
		socket.close();
	}
	
	private boolean continueThread() {
		return !graceFullInterrupt;
	}
	
	@Override
	public void run() {
		while(continueThread()) {
			byte[] buffer = new byte[MAX_UDP_HEADER_LENGTH + MAX_UTP_PACKET_LENGTH];
			DatagramPacket dgpkt = new DatagramPacket(buffer, buffer.length);
			try {
				socket.receive(dgpkt);
				queueable.recievePacket(dgpkt);

			} catch (IOException exp) {
				//TODO: what to do here?
				break;
			}
		}
		System.out.println("RECIEVER OUT");
		
	}

}
