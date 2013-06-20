package ch.uzh.csg.utp4j.channels.impl;


import static ch.uzh.csg.utp4j.data.UtpPacketUtils.MAX_UDP_HEADER_LENGTH;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.MAX_UTP_PACKET_LENGTH;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;




public class UtpRecieveRunnable implements Runnable {


	private DatagramSocket socket;
	private UtpPacketRecievable queueable;
	

	public UtpRecieveRunnable(DatagramSocket socket, UtpPacketRecievable queueable) {
		this.socket = socket;
		this.queueable = queueable;
	}
		

	@Override
	public void run() {
		while(true) {
			
			byte[] buffer = new byte[MAX_UDP_HEADER_LENGTH + MAX_UTP_PACKET_LENGTH];
			DatagramPacket dgpkt = new DatagramPacket(buffer, buffer.length);
			try {
				System.out.println("runnable listening");
				socket.receive(dgpkt);
				System.out.println("runnable rec. packet");
				queueable.recievePacket(dgpkt);

			} catch (IOException exp) {
				System.out.println("break");

				break;
			}
		}

		
	}

}
