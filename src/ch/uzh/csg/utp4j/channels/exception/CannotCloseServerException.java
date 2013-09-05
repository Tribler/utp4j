package ch.uzh.csg.utp4j.channels.exception;

import java.util.Collection;
import java.util.LinkedList;

import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.impl.recieve.ConnectionIdTriplet;

public class CannotCloseServerException extends RuntimeException {

	private Collection<UtpSocketChannel> openChannels = new LinkedList<UtpSocketChannel>();
	
	public CannotCloseServerException(Collection<ConnectionIdTriplet> values) {
		for (ConnectionIdTriplet connectionIdTriplet : values) {
			openChannels.add(connectionIdTriplet.getChannel());
		}
	}
	
	public Collection<UtpSocketChannel> getOpenChannels() {
		return openChannels;
	}

	/**
	 * serialVersion
	 */
	private static final long serialVersionUID = 2445217336379114305L;

		

}
