package ch.uzh.csg.utp4j.channels.impl.alg;

public class MinimumDelay {

	private long timeStamp = 0;
	private long minDelay = 0;
	
	private static long MINIMUM_DIFFERENCE_TIMESTAMP_MICROSEC = 120000000L;
	
	public long getMinDelay() {
		return minDelay;
	}

	public void updateMinDelay(long difference, long timestamp) {

		if ((timeStamp - this.timeStamp >= MINIMUM_DIFFERENCE_TIMESTAMP_MICROSEC) || (this.timeStamp == 0 && this.minDelay == 0)) {
			this.timeStamp = timestamp;
			this.minDelay = difference;
		} else {
			if (difference < this.minDelay) {
				this.timeStamp = timestamp;
				this.minDelay = difference;
			}
		}
		
	}

}
