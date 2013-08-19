package ch.uzh.csg.utp4j.channels.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import ch.uzh.csg.utp4j.channels.UtpServerSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketState;
import ch.uzh.csg.utp4j.channels.impl.recieve.ConnectionIdTriplet;
import ch.uzh.csg.utp4j.channels.impl.recieve.UtpPacketRecievable;
import ch.uzh.csg.utp4j.channels.impl.recieve.UtpRecieveRunnable;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

public class UtpServerSocketChannelImpl extends UtpServerSocketChannel implements UtpPacketRecievable {
	
	protected Queue<DatagramPacket> synQueue = new ConcurrentLinkedQueue<DatagramPacket>(); 
	private UtpRecieveRunnable listenRunnable;
	private Map<Integer, ConnectionIdTriplet> connectionIds = new HashMap<Integer, ConnectionIdTriplet>();
	private boolean listenRunnerStarted = false;

	@Override
	protected UtpSocketChannel acceptImpl() throws IOException {
		
		if (!listenRunnerStarted) {
			Thread listenRunner = new Thread(getListenRunnable());
			listenRunner.start();
			listenRunnerStarted = true;
		}
		
		DatagramPacket packet = null;
		UtpSocketChannelImpl utpChannel = null;
		
		do {
			packet = synQueue.poll();	
			if (packet != null) {
				System.out.println("SERVER HAS PKT");
				utpChannel = (UtpSocketChannelImpl) UtpSocketChannel.open();
				utpChannel.setDgSocket(getSocket());
				utpChannel.recievePacket(packet);
				boolean registered = registerChannel(utpChannel);
				
				/* Collision in Connection ids or failed to ack. 
				 * Ignore Syn Packet and let other side handle the issue. */
				if (!registered) {
					utpChannel = null;
				}
			}
		} while (continueAcceptLoop(packet, utpChannel));
		
		
		return utpChannel;
	}
	
	
	private boolean continueAcceptLoop(DatagramPacket pkt, UtpSocketChannel ch) {
		return blocking && pkt == null && (ch == null || !ch.isConnected());
	}
	
	@Override
	public void recievePacket(DatagramPacket packet) {
		if (UtpPacketUtils.isSynPkt(packet)) {
			synQueue.add(packet);
		} else {
			UtpPacket utpPacket = UtpPacketUtils.extractUtpPacket(packet);
			ConnectionIdTriplet triplet = connectionIds.get(utpPacket.getConnectionId() & 0xFFFF);
			if (triplet != null) {
				triplet.getChannel().recievePacket(packet);
			}			
		}
	}
		
	protected UtpRecieveRunnable getListenRunnable() {
		return listenRunnable;
	}
	
	public void setListenRunnable(UtpRecieveRunnable listenRunnable) {
		this.listenRunnable = listenRunnable;
	}

	private boolean registerChannel(UtpSocketChannelImpl channel) {
		ConnectionIdTriplet triplet = new ConnectionIdTriplet(
				channel, channel.getConnectionIdRecieving(), channel.getConnectionIdsending());
		
		if (isChannelRegistrationNecessary(channel)) {
			connectionIds.put((int) (channel.getConnectionIdRecieving() & 0xFFFF), triplet);
			return true;
		}
		
		/* Connection id collision found or not been able to ack.
		 *  ignore this syn packet */
		return false;
	}
	
	private boolean isChannelRegistrationNecessary(UtpSocketChannelImpl channel) {
		return connectionIds.get(channel.getConnectionIdRecieving()) == null
				&& channel.getState() != UtpSocketState.SYN_ACKING_FAILED;
	}


	@Override
	public void close() {
		synQueue.clear();
		listenRunnable.graceFullInterrupt();
	}

}
