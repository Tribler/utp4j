package ch.uzh.csg.utp4j.data;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_UINT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUint;
import ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil;
import ch.uzh.csg.utp4j.data.bytes.exceptions.ByteOverflowException;

public class MicroSecondsTimeStamp {

	private static long initDateMillis = System.currentTimeMillis();
	private static long startNs = System.nanoTime();
	
	public int utpTimeStamp() {
		int returnStamp;
		//TODO: if performance issues, try bitwise & operator since constant MAX_UINT equals 0xFFFFFF
		// (see http://en.wikipedia.org/wiki/Modulo_operation#Performance_issues )
		long stamp = timeStamp() % UnsignedTypesUtil.MAX_UINT;
		try {
			returnStamp  = longToUint(stamp);
		} catch (ByteOverflowException exp) {
			stamp = stamp % MAX_UINT;
			returnStamp = longToUint(stamp);
			System.out.println("Exception in timestamp");
		}
		return returnStamp;
	}
	
	public int UtpDifference(int othertimestamp) {
		int nowTime = utpTimeStamp();
		long nowTimeL = (long) (nowTime & 0x00000000FFFFFFFF);
		long otherTimeL = (long) (othertimestamp & 0x00000000FFFFFFFF);
		long differenceL = nowTimeL - otherTimeL;
		return (int) (differenceL & 0xFFFFFFFF);
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
