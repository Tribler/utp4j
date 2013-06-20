package ch.uzh.csg.utp4j.data;

import ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil;

public class MicroSecondsTimeStamp {

	private static long initDateMillis = System.currentTimeMillis();
	private static long startNs = System.nanoTime();
	
	public long utpTimeStamp() {
		//TODO: if performance issues, try bitwise & operator since constant MAX_UINT equals 0xFFFFFF
		// (see http://en.wikipedia.org/wiki/Modulo_operation#Performance_issues )
		return timeStamp() % UnsignedTypesUtil.MAX_UINT;
	}
	
	public long timeStamp() {
		
		long currentNs = System.nanoTime();
		long deltaMs = (currentNs - startNs)/1000;
		
		return (initDateMillis*1000 + deltaMs);
	}
	
	
	public long getBegin() {
		return (initDateMillis * 1000);
	}
	
	public long getStartNs() {
		return startNs;
	}
	
	public long getInitDateMs() {
		return initDateMillis;
	}

}
