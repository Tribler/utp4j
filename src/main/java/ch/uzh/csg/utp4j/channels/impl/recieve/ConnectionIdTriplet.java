package ch.uzh.csg.utp4j.channels.impl.recieve;

import ch.uzh.csg.utp4j.channels.impl.UtpSocketChannelImpl;


public class ConnectionIdTriplet {

	private UtpSocketChannelImpl channel;
	//incomming connection ID
	private long inComming;
	//outgoing is incomming connection ID + 1 as specified
	private long outGoing;

	public UtpSocketChannelImpl getChannel() {
		return channel;
	}

	public ConnectionIdTriplet(UtpSocketChannelImpl channel, long inComming, long outGoing) {
		this.channel = channel;
		this.inComming = inComming;
		this.outGoing = outGoing;
	}
		
	@Override
	public boolean equals(Object other) {
		
		if (other == null) {
			return false;
		} else if (this == other) {
			return true;
		} else if (!(other instanceof ConnectionIdTriplet)) {
			return false;
		} else {
			ConnectionIdTriplet otherTriplet = (ConnectionIdTriplet) other;
			return this.inComming == otherTriplet.getInComming() &&
				   this.outGoing == otherTriplet.getOutGoing();
		}
	}
	
	@Override
	public int hashCode() {
		int hash = 23;
		hash = hash * 31 + (int) outGoing;
		hash = hash * 31 + (int) inComming;
		return hash;
	}
	
	public void setChannel(UtpSocketChannelImpl channel) {
		this.channel = channel;
	}
	
	
	public long getInComming() {
		return inComming;
	}
	
	
	public void setInComming(long inComming) {
		this.inComming = inComming;
	}
	
	
	public long getOutGoing() {
		return outGoing;
	}
	
	
	public void setOutgoing(long outGoing) {
		this.outGoing = outGoing;
	}
}
