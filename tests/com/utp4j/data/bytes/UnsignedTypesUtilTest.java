package com.utp4j.data.bytes;

import static org.junit.Assert.*;

import org.junit.Test;

import com.utp4j.data.bytes.UnsignedTypesUtil;
import com.utp4j.data.bytes.exceptions.ByteOverflowException;
import com.utp4j.data.bytes.exceptions.SignedNumberException;

import static com.utp4j.data.bytes.UnsignedTypesUtil.*;

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
		String uByteMaxString = toBinaryString(longToUbyte(MAX_UBYTE), 8);
		assertEquals("11111111", uByteMaxString);
		
		String uByteMinString = toBinaryString(longToUbyte(0L), 1);
		assertEquals("0", uByteMinString);
		
		String inbetweenExpected = "1111100";
		String uByteBetweenString = toBinaryString(longToUbyte(124), inbetweenExpected.length());
		assertEquals(inbetweenExpected, uByteBetweenString);
		
		try {
			@SuppressWarnings("unused")
			String uByteNegativeString = toBinaryString(longToUbyte(-5), 0);
			fail("Expected SignedNumberException, but none was thrown.");
		} catch (SignedNumberException exp) {}
		
		try {
			@SuppressWarnings("unused")
			String uByteOverflowString = toBinaryString(longToUbyte(MAX_UBYTE + 1), 0);
			fail("Expected ByteOverflowException, but none was thrown.");	
		} catch (ByteOverflowException exp) {}
		
	}
	
	/**
	 * min/max, in between, negative and overflow tests for unsigned short (16bit) workaround. 
	 */
	@Test
	public void testToUShort() {
		String uShortMaxString = toBinaryString(longToUshort(MAX_USHORT), 16);
		assertEquals("1111111111111111", uShortMaxString);
		
		String uShortMinString = toBinaryString(longToUshort(0L), 1);
		assertEquals("0", uShortMinString);
		
		String inBetweenExpected = "1111111111111";
		String uShortInBetweenString = toBinaryString(longToUshort(8191L), inBetweenExpected.length());
		assertEquals(inBetweenExpected, uShortInBetweenString);
		
		try {
			@SuppressWarnings("unused")
			String uShortNegativeString = toBinaryString(longToUshort(-5L), 1);
			fail("Expected SignedNumberException, but none was thrown.");
		} catch (SignedNumberException exp) {};
		
		try {
			@SuppressWarnings("unused")
			String uShortOverflowString = toBinaryString(longToUshort(MAX_USHORT + 25), 16);
			fail("Expected ByteOverflowException, but none was thrown.");	
		} catch (ByteOverflowException exp) {}
		
	}
	
	/**
	 * min/max, in between, negative and overflow tests for unsigned integer (32bit) workaround. 
	 */
	@Test
	public void testToUint() {
		String uIntMaxString = toBinaryString(longToUint(MAX_UINT), 32);
		assertEquals("11111111111111111111111111111111", uIntMaxString);
		
		String uIntMinString = toBinaryString(longToUint(0L), 1);
		assertEquals("0", uIntMinString);
		
		String inBetweenExpected = "11111111111111111111111111111";
		String uIntInBetweenString = toBinaryString(longToUint(536870911L), inBetweenExpected.length());
		assertEquals(inBetweenExpected, uIntInBetweenString);
		
		try {
			@SuppressWarnings("unused")
			String uIntNegativeString = toBinaryString(longToUint(-5L), 1);
			fail("Expected SignedNumberException, but none was thrown.");
		} catch (SignedNumberException exp) {};
		
		try {
			@SuppressWarnings("unused")
			String uIntOverflowString = toBinaryString(longToUint(MAX_UINT + 1254), 16);
			fail("Expected ByteOverflowException, but none was thrown.");	
		} catch (ByteOverflowException exp) {}
		
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
