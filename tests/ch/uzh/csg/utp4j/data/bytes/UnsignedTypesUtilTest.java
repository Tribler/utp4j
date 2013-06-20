package ch.uzh.csg.utp4j.data.bytes;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_UBYTE;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_UINT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.bytesToUint;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.bytesToUshort;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUbyte;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUint;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import ch.uzh.csg.utp4j.data.bytes.exceptions.ByteOverflowException;
import ch.uzh.csg.utp4j.data.bytes.exceptions.SignedNumberException;

public class UnsignedTypesUtilTest {

	
	/**
	 * Tests {@link UnsignedTypesUtil} 's defined constants for correctness. 
	 */
	@Test
	public void testConstants() {
		assertEquals(255L, MAX_UBYTE);
		assertEquals(65535L, MAX_USHORT);
		assertEquals(4294967295L, MAX_UINT);
	}
	
	/**
	 * min/max, in between, negative and overflow tests for unsigned byte workaround.
	 */
	@Test 
	public void testToUbyte() {
		String uByteMaxString = toBinaryString(longToUbyte(MAX_UBYTE));
		assertEquals("00000000000000000000000011111111", uByteMaxString);
		
		String uByteMinString = toBinaryString(longToUbyte(0L));
		assertEquals("00000000000000000000000000000000", uByteMinString);

		String inbetweenExpected = "00000000000000000000000001111100";
		String uByteBetweenString = toBinaryString(longToUbyte(124));
		assertEquals(inbetweenExpected, uByteBetweenString);
		
		try {
			@SuppressWarnings("unused")
			String uByteNegativeString = toBinaryString(longToUbyte(-5));
			fail("Expected SignedNumberException, but none was thrown.");
		} catch (SignedNumberException exp) {}
		
		try {
			@SuppressWarnings("unused")
			String uByteOverflowString = toBinaryString(longToUbyte(MAX_UBYTE + 1));
			fail("Expected ByteOverflowException, but none was thrown.");	
		} catch (ByteOverflowException exp) {}
		
	}
	
	/**
	 * min/max, in between, negative and overflow tests for unsigned short (16bit) workaround. 
	 */
	@Test
	public void testToUShort() {
		String uShortMaxString = toBinaryString(longToUshort(MAX_USHORT));
		assertEquals("00000000000000001111111111111111", uShortMaxString);
		
		String uShortMinString = toBinaryString(longToUshort(0L));
		assertEquals("00000000000000000000000000000000", uShortMinString);

		String inBetweenExpected = "00000000000000000001111111111111";
		String uShortInBetweenString = toBinaryString(longToUshort(8191L));
		assertEquals(inBetweenExpected, uShortInBetweenString);
		
		try {
			@SuppressWarnings("unused")
			String uShortNegativeString = toBinaryString(longToUshort(-5L));
			fail("Expected SignedNumberException, but none was thrown.");
		} catch (SignedNumberException exp) {};
		
		try {
			@SuppressWarnings("unused")
			String uShortOverflowString = toBinaryString(longToUshort(MAX_USHORT + 25));
			fail("Expected ByteOverflowException, but none was thrown.");	
		} catch (ByteOverflowException exp) {}
		
	}
	
	/**
	 * min/max, in between, negative and overflow tests for unsigned integer (32bit) workaround. 
	 */
	@Test
	public void testToUint() {
		String uIntMaxString = toBinaryString(longToUint(MAX_UINT));
		assertEquals("11111111111111111111111111111111", uIntMaxString);
		
		String uIntMinString = toBinaryString(longToUint(0L));
		assertEquals("00000000000000000000000000000000", uIntMinString);
		
		String inBetweenExpected = "00011111111111111111111111111111";
		String uIntInBetweenString = toBinaryString(longToUint(536870911L));
		assertEquals(inBetweenExpected, uIntInBetweenString);
		
		try {
			@SuppressWarnings("unused")
			String uIntNegativeString = toBinaryString(longToUint(-5L));
			fail("Expected SignedNumberException, but none was thrown.");
		} catch (SignedNumberException exp) {};
		
		try {
			@SuppressWarnings("unused")
			String uIntOverflowString = toBinaryString(longToUint(MAX_UINT + 1254));
			fail("Expected ByteOverflowException, but none was thrown.");	
		} catch (ByteOverflowException exp) {}
		
	}
	
	/**
	 * Tests if two bytes joined together return the supposed value
	 */
	@Test
	public void testShortTwoBytes() {
		byte one = longToUbyte(MAX_UBYTE);
		byte two = longToUbyte(0);
		short result = bytesToUshort(one, two);
		String resultString = toBinaryString(result);
		assertEquals("00000000000000001111111100000000", resultString);
	}
	
	@Test
	public void testInt4Bytes() {
		byte one = longToUbyte(MAX_UBYTE);
		byte two = 0;
		byte three = longToUbyte(MAX_UBYTE);
		byte four = 0;
		int result = bytesToUint(one, two, three, four);
		String resultString = toBinaryString(result);
		assertEquals("11111111000000001111111100000000", resultString);
	}
	
	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @return String binary representation. 
	 */
	private static String toBinaryString(int value) {
		String result = Integer.toBinaryString(value);
		
		StringBuilder buf = new StringBuilder();
		for (int i = 0; (i + result.length()) < 32; i++) {
			buf.append("0");
		}
		buf.append(result);
		return buf.toString();
	}
	
	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @return String binary representation. 
	 */
	private static String toBinaryString(byte value) {
		return toBinaryString((value & 0xFF));
	}
	
	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @return String binary representation. 
	 */
	private static String toBinaryString(short value) {
		return toBinaryString((value & 0xFFFF));
	}
	
}
