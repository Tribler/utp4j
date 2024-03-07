/* Copyright 2013 Ivan Iljkic
*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License. You may obtain a copy of
* the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations under
* the License.
*/
package net.utp4j.data.bytes;

import static net.utp4j.data.bytes.UnsignedTypesUtil.MAX_UBYTE;
import static net.utp4j.data.bytes.UnsignedTypesUtil.MAX_UINT;
import static net.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static net.utp4j.data.bytes.UnsignedTypesUtil.bytesToUint;
import static net.utp4j.data.bytes.UnsignedTypesUtil.bytesToUshort;
import static net.utp4j.data.bytes.UnsignedTypesUtil.longToUbyte;
import static net.utp4j.data.bytes.UnsignedTypesUtil.longToUint;
import static net.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;
import static net.utp4j.data.bytes.BinaryToStringTestHelper.toBinaryString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import net.utp4j.data.bytes.exceptions.ByteOverflowException;
import net.utp4j.data.bytes.exceptions.SignedNumberException;

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
		assertEquals("11111111", uByteMaxString);
		
		String uByteMinString = toBinaryString(longToUbyte(0L));
		assertEquals("00000000", uByteMinString);

		String inbetweenExpected = "01111100";
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
		assertEquals("1111111111111111", uShortMaxString);
		
		String uShortMinString = toBinaryString(longToUshort(0L));
		assertEquals("0000000000000000", uShortMinString);

		String inBetweenExpected = "0001111111111111";
		String uShortInBetweenString = toBinaryString(longToUshort(8191L));
		assertEquals(inBetweenExpected, uShortInBetweenString);
		
		try {
			@SuppressWarnings("unused")
			String uShortNegativeString = toBinaryString(longToUshort(-5L));
			fail("Expected SignedNumberException, but none was thrown.");
		} catch (SignedNumberException exp) {}
		
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
		} catch (SignedNumberException exp) {}
		
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
		assertEquals("1111111100000000", resultString);
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
	

	
}
