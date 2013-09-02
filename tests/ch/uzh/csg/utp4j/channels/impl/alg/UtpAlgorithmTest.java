package ch.uzh.csg.utp4j.channels.impl.alg;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;

import org.junit.Test;
import org.junit.runner.RunWith;

import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;

@RunWith(org.mockito.runners.MockitoJUnitRunner.class)
public class UtpAlgorithmTest {

	/**
	 * Packet size testing, dynamic, linear
	 */
	@Test
	public void testDynamicPacketSize() {
		
		MinimumDelay mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getMinDelay()).thenReturn(500000L);
		
		/* when delay is 50ms, then packetsize = 
		 * (delay/max_delay)*(max_packet-min_packet)+min_packet */
		MicroSecondsTimeStamp stamper = new MicroSecondsTimeStamp();
		UtpAlgorithm alg = new UtpAlgorithm(stamper, new InetSocketAddress(51235));
		alg.packetSizeModus = PacketSizeModus.DYNAMIC_LINEAR;
		alg.setMinDelay(mockMinDelay);
		assertEquals(811, alg.sizeOfNextPacket());
		
		mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getMinDelay()).thenReturn(1000000L);
		alg.setMinDelay(mockMinDelay);
		assertEquals(150, alg.sizeOfNextPacket());
		
		mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getMinDelay()).thenReturn(0L);
		alg.setMinDelay(mockMinDelay);
		assertEquals(1472, alg.sizeOfNextPacket());
		
		mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getMinDelay()).thenReturn(300000L);
		alg.setMinDelay(mockMinDelay);
		assertEquals(1076, alg.sizeOfNextPacket());
		
		mockMinDelay = mock(MinimumDelay.class);
		when(mockMinDelay.getMinDelay()).thenReturn(700000L);
		alg.setMinDelay(mockMinDelay);
		assertEquals(547, alg.sizeOfNextPacket());
		
	}
	
	@Test
	public void testSelectiveAck() {
		
	}
	

	
}
