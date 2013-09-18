package ch.uzh.csg.utp4j.channels.impl.conn;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import ch.uzh.csg.utp4j.channels.UtpSocketState;
import ch.uzh.csg.utp4j.channels.impl.UtpSocketChannelImpl;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration;
import ch.uzh.csg.utp4j.data.UtpPacket;

public class ConnectionTimeOutRunnable implements Runnable {

	private UtpPacket synPacket;
	private UtpSocketChannelImpl channel;
	private volatile ReentrantLock lock;

	public ConnectionTimeOutRunnable(UtpPacket packet,
			UtpSocketChannelImpl channel, ReentrantLock lock) {
		this.synPacket = packet;
		this.lock = lock;
		this.channel = channel;
	}

	@Override
	public void run() {
		lock.lock();
		int attempts = channel.getConnectionAttempts();
		System.out.println("attempt: " + attempts);
		if (channel.getState() == UtpSocketState.SYN_SENT) {
			try {
				if (attempts < UtpAlgConfiguration.MAX_CONNECTION_ATTEMPTS) {
					channel.incrementConnectionAttempts();
					System.out.println("REATTEMPTING CONNECTION");
					channel.sendPacket(getPacket());
				} else {
					channel.connectionFailed(new SocketTimeoutException());
				}
			} catch (IOException e) {
				if (attempts >= UtpAlgConfiguration.MAX_CONNECTION_ATTEMPTS) {
					channel.connectionFailed(e);
				} // else ignore, try in next attempt
			}
		}
		lock.unlock();

	}

	private DatagramPacket getPacket() throws SocketException {
		byte[] utpPacketBytes = synPacket.toByteArray();
		int length = synPacket.getPacketLength();
		DatagramPacket pkt = new DatagramPacket(utpPacketBytes, length,
				channel.getRemoteAdress());
		return pkt;
	}

}
