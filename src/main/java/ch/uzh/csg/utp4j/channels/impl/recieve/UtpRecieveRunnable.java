package ch.uzh.csg.utp4j.channels.impl.recieve;


import static ch.uzh.csg.utp4j.data.UtpPacketUtils.MAX_UDP_HEADER_LENGTH;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.MAX_UTP_PACKET_LENGTH;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class UtpRecieveRunnable extends Thread implements Runnable {


	private DatagramSocket socket;
	private UtpPacketRecievable packetReciever;
	private boolean graceFullInterrupt = false;
	
	private final static Logger log = LoggerFactory.getLogger(UtpRecieveRunnable.class);
	

	public UtpRecieveRunnable(DatagramSocket socket, UtpPacketRecievable queueable) {
		this.socket = socket;
		this.packetReciever = queueable;
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
				//the packet will be filled here
				socket.receive(dgpkt);
				//hand packet to the queue
				packetReciever.recievePacket(dgpkt);
//				(new UtpPassPacket(dgpkt, queueable)).start();

			} catch (IOException exp) {
				if (graceFullInterrupt) {
					log.debug("socket closing");
					break;					
				} else {
					exp.printStackTrace();					
				}
			}
		}
		log.debug("RECIEVER OUT");
		
	}

}
