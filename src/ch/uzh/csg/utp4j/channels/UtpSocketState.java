package ch.uzh.csg.utp4j.channels;

/**
 * Possible states which a Utp Socket can be in.
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public enum UtpSocketState {
	
		/**
		 * Indicates that a syn packet has been send.
		 */
		SYN_SENT,
		
		/**
		 * Indicates that a connection has been established.
		 */
		CONNECTED,
		
		/**
		 * Indicates that no connection is established.
		 */
		CLOSED,
		
		/**
		 * Indicates that a SYN has been recieved but could not acked.
		 */
		SYN_ACKING_FAILED, 
		
		/**
		 * Indicates that the fin Packet has been send.
		 */
		FIN_SEND,
}
