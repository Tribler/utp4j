package ch.uzh.csg.utp4j.channels.impl.alg;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;

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
		UtpAlgorithm alg = new UtpAlgorithm();
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
}
