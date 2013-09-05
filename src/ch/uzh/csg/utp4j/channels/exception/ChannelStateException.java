package ch.uzh.csg.utp4j.channels.exception;

import java.net.SocketAddress;

import ch.uzh.csg.utp4j.channels.UtpSocketState;

public class ChannelStateException extends RuntimeException {
	

	private static final long serialVersionUID = -6506270718267816636L;
	
	private UtpSocketState state;
	private SocketAddress addresse;

	public ChannelStateException(String msg) {
		super(msg);
	}

	public UtpSocketState getState() {
		return state;
	}

	public void setState(UtpSocketState state) {
		this.state = state;
	}

	public SocketAddress getAddresse() {
		return addresse;
	}

	public void setAddresse(SocketAddress addresse) {
		this.addresse = addresse;
	}
 
}
