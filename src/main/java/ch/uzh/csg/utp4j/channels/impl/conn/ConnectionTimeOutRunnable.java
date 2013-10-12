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
		channel.resendSynPacket(synPacket);
	}


}
