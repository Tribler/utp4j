package ch.uzh.csg.utp4j.data.bytes;

import static ch.uzh.csg.utp4j.data.UtpPacketUtils.DATA;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.FIN;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.RESET;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.STATE;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.SYN;
import static ch.uzh.csg.utp4j.data.UtpPacketUtils.joinByteArray;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtpPacketUtilsTest {
	
	/**
	 * Testing with type 0,1,2,3,4 which correspondents to: ST_DATA, ST_FIN, ST_STATE, ST_RESET, ST_SYN 
	 * and provoking exceptions for arguments out of range. 
	 * see http://bittorrent.org/beps/bep_0029.html
	 */
	@Test
	public void testTypeAndVersionByte() {
		String type0excpected = "1";
		String type1excpected = "10001";
		String type2excpected = "100001";
		String type3excpected = "110001";
		String type4excpected = "1000001";
		
		String actual0 = toBinaryString(DATA, type0excpected.length());
		String actual1 = toBinaryString(FIN, type1excpected.length());
		String actual2 = toBinaryString(STATE, type2excpected.length());
		String actual3 = toBinaryString(RESET, type3excpected.length());
		String actual4 = toBinaryString(SYN,  type4excpected.length());
		
		assertEquals(type0excpected, actual0);
		assertEquals(type1excpected, actual1);
		assertEquals(type2excpected, actual2);
		assertEquals(type3excpected, actual3);
		assertEquals(type4excpected, actual4);
		
	}
	
	/**
	 * Tests joinArray() with two arrays.
	 */
	@Test
	public void testJoinArray() {
		byte[] array1 = { 0, 1, 2};
		byte[] array2 = {3, 4, 5};
		
		byte[] returnArray = joinByteArray(array1, array2);
		
		assertEquals(6, returnArray.length);
		
		for (int i = 0; i < returnArray.length; i++) {
			assertEquals(i, returnArray[i]);
		}

	}
	
	/**
	 * Tests joinArray() with two arrays, first array is empty.
	 */
	@Test
	public void testJoinArrayEmptyFrist() {
		byte[] array1 = {};
		byte[] array2 = {3, 4, 5};
		
		byte[] returnArray = joinByteArray(array1, array2);
		
		assertEquals(3, returnArray.length);
		
		assertEquals(3, returnArray[0]);
		assertEquals(4, returnArray[1]);		
		assertEquals(5, returnArray[2]);

	}
	
	
	/**
	 * Tests joinArray() with two arrays, second array is empty.
	 */
	@Test
	public void testJoinArrayEmptySecond() {
		byte[] array1 = {3, 4, 5};
		byte[] array2 = {};
		
		byte[] returnArray = joinByteArray(array1, array2);
		
		assertEquals(3, returnArray.length);
		
		assertEquals(3, returnArray[0]);
		assertEquals(4, returnArray[1]);		
		assertEquals(5, returnArray[2]);
	}
	
	/**
	 * Tests joinArray() with two arrays, both are empty.
	 */
	@Test
	public void testJoinArrayEmptyBoth() {
		byte[] array1 = {};
		byte[] array2 = {};
		
		byte[] returnArray = joinByteArray(array1, array2);
		
		assertEquals(0, returnArray.length);
	}
	
	/**
	 * Tests joinArray() with two arrays, both are null.
	 */
	@Test
	public void testJoinArrayBothNull() {

		byte[] returnArray = joinByteArray(null, null);
		
		assertEquals(0, returnArray.length);
	}
	
	/**
	 * Tests joinArray() with two arrays, first is null.
	 */
	@Test
	public void testJoinFirstIsNull() {
		byte[] array1 = null;
		byte[] array2 = {3, 4, 5};
		
		byte[] returnArray = joinByteArray(array1, array2);
		
		assertEquals(3, returnArray.length);
		
		assertEquals(3, returnArray[0]);
		assertEquals(4, returnArray[1]);		
		assertEquals(5, returnArray[2]);

	}
	
	/**
	 * Tests joinArray() with two arrays, second is null.
	 */
	@Test
	public void testJoinSecondIsNull() {
		byte[] array2 = null;
		byte[] array1 = {3, 4, 5};
		
		byte[] returnArray = joinByteArray(array1, array2);
		
		assertEquals(3, returnArray.length);
		
		assertEquals(3, returnArray[0]);
		assertEquals(4, returnArray[1]);		
		assertEquals(5, returnArray[2]);

	}

	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @param bitLegnth length in bits of the return string. starting from index 0 to index (bitLength-1)
	 * @return String binary representation. 
	 */
	private static String toBinaryString(long value, int bitLegnth) {
		return Long.toBinaryString(value).substring(0, bitLegnth);
	}

}
