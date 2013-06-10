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
		CS_SYN_SENT,
		
		/**
		 * Indicates that a connection has been established.
		 */
		CS_CONNECTED,
}
