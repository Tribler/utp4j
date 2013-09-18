package ch.uzh.csg.utp4j.channels.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import ch.uzh.csg.utp4j.channels.UtpServerSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.UtpSocketState;
import ch.uzh.csg.utp4j.channels.exception.CannotCloseServerException;
import ch.uzh.csg.utp4j.channels.futures.UtpAcceptFuture;
import ch.uzh.csg.utp4j.channels.impl.accept.UtpAcceptFutureImpl;
import ch.uzh.csg.utp4j.channels.impl.recieve.ConnectionIdTriplet;
import ch.uzh.csg.utp4j.channels.impl.recieve.UtpPacketRecievable;
import ch.uzh.csg.utp4j.channels.impl.recieve.UtpRecieveRunnable;
import ch.uzh.csg.utp4j.data.UtpPacket;
import ch.uzh.csg.utp4j.data.UtpPacketUtils;

public class UtpServerSocketChannelImpl extends UtpServerSocketChannel implements UtpPacketRecievable {
	
	private UtpRecieveRunnable listenRunnable;
	private Queue<UtpAcceptFutureImpl> acceptQueue = new LinkedList<UtpAcceptFutureImpl>();
	private Map<Integer, ConnectionIdTriplet> connectionIds = new HashMap<Integer, ConnectionIdTriplet>();
	private boolean listenRunnerStarted = false;

	@Override
	protected UtpAcceptFuture acceptImpl() throws IOException {
		
		if (!listenRunnerStarted) {
			Thread listenRunner = new Thread(getListenRunnable());
			listenRunner.start();
			listenRunnerStarted = true;
		}
		
		UtpAcceptFutureImpl future;
		try {
			future = new UtpAcceptFutureImpl();
			acceptQueue.add(future);
			return future;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	private void synRecieved(DatagramPacket packet) {
		if (handleDoubleSyn(packet)) {
			return;
		}
		if (packet != null && acceptQueue.peek() != null) {
			boolean registered = false;
			UtpAcceptFutureImpl future = acceptQueue.poll();
			UtpSocketChannelImpl utpChannel = null;
			try {
				utpChannel = (UtpSocketChannelImpl) UtpSocketChannel.open();
				utpChannel.setDgSocket(getSocket());
				utpChannel.recievePacket(packet);
				utpChannel.setServer(this);
				registered = registerChannel(utpChannel);
			} catch (IOException e) {
				future.setIOException(e);
			}
			
			/* Collision in Connection ids or failed to ack. 
			 * Ignore Syn Packet and let other side handle the issue. */
			if (!registered) {
				utpChannel = null;
			}
			future.synRecieved(utpChannel);
		}
	}
		
	private boolean handleDoubleSyn(DatagramPacket packet) {
		UtpPacket pkt = UtpPacketUtils.extractUtpPacket(packet);
		int connId = pkt.getConnectionId();
		connId = (connId & 0xFFFF) + 1;
		ConnectionIdTriplet triplet = connectionIds.get(connId);
		if (triplet != null) {
			triplet.getChannel().recievePacket(packet);
			return true;
		}
		
		return false;
	}

	@Override
	public void recievePacket(DatagramPacket packet) {
		if (UtpPacketUtils.isSynPkt(packet)) {
			synRecieved(packet);
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
		if (connectionIds.isEmpty()) {
			listenRunnable.graceFullInterrupt();			
		} else {
			throw new CannotCloseServerException(connectionIds.values());
		}
	}

	public void unregister(UtpSocketChannelImpl utpSocketChannelImpl) {
		connectionIds.remove((int) utpSocketChannelImpl.getConnectionIdRecieving() & 0xFFFF);
	}

}
