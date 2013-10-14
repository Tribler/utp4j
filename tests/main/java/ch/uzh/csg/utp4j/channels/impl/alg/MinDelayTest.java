package ch.uzh.csg.utp4j.channels.impl.alg;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MinDelayTest {

	/**
	 * tests min delay. 
	 */
	@Test
	public void testMinDelay() {
		MinimumDelay delay = new MinimumDelay();
		delay.updateOurDelay(5, 1);
		delay.updateOurDelay(2, 2);
		delay.updateOurDelay(5, 3);
		delay.updateOurDelay(8, 12);
		assertEquals(2, delay.getCorrectedMinDelay());
	}
	
	/**
	 * Tests the min delay, when its outdated, 
	 * it should be updated to the most recent. 
	 */
	@Test
	public void testMinDelayOutdated() {
		MinimumDelay delay = new MinimumDelay();
		// oldest minimum has timestamp 2.
		delay.updateOurDelay(5, 1);
		delay.updateOurDelay(2, 2);
		delay.updateOurDelay(5, 3);
		delay.updateOurDelay(8, 12);
		// new minimum should be 20, with timestamp MinimumDelay.MINIMUM_DIFFERENCE_TIMESTAMP_MICROSEC + x | x >= 2;
		delay.updateOurDelay(20, UtpAlgConfiguration.MINIMUM_DIFFERENCE_TIMESTAMP_MICROSEC + 2);
		assertEquals(20, delay.getCorrectedMinDelay());
	}
	
	/**
	 * Tests clock drift correction
	 */
	@Test
	public void testClockDriftCorrection() {
		MinimumDelay delay = new MinimumDelay();
		// our minDelay is 5
		delay.updateOurDelay(5, 1);
		assertEquals(5, delay.getCorrectedMinDelay());
		delay.updateTheirDelay(8, 2);
		// our delay should still be 5
		assertEquals(5, delay.getCorrectedMinDelay());
		
		// now remote clock ticks faster, their delay decreases
		delay.updateTheirDelay(7, 10);
		
		// our delay should be 6 now.
		assertEquals(6, delay.getCorrectedMinDelay());
		
	}

}
