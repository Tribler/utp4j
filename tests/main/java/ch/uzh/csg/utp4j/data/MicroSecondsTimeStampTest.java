package ch.uzh.csg.utp4j.data;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MicroSecondsTimeStampTest {

	@Test
	public void test() throws InterruptedException {
		
		MicroSecondsTimeStamp stamp1 = new MicroSecondsTimeStamp();
		MicroSecondsTimeStamp stamp2 = new MicroSecondsTimeStamp();
		
		long stamp1Ms = stamp1.timeStamp();
		
		long stampedMilli = stamp1Ms/1000;
		long currentMilli = System.currentTimeMillis();
			
		assertTrue("Expecting timestamp()/1000 and System.currentTimeMillis() " +
				"to have less than 100 milliseconds difference", (currentMilli - stampedMilli) < 100);		
		
		assertTrue("Expecting timestamp()/1000 and System.currentTimeMillis() " +
				"to have non negative difference", (currentMilli - stampedMilli) >= 0);
		
		long stamp2Ms = stamp2.timeStamp();

		assertTrue("Expecting stamp1 to be older than stamp2 and not equal", stamp2Ms - stamp1Ms > 0);
		
	}
}
