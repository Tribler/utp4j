package ch.uzh.csg.utp4j.data.bytes;

public class BinaryToStringTestHelper {
	
	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @return String binary representation. 
	 */
	public static String toBinaryString(int value) {
		return toBinaryString(value, 32);
	}

	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @return String binary representation. 
	 */
	private static String toBinaryString(int value, int length) {
		String result = Integer.toBinaryString(value);
		
		StringBuilder buf = new StringBuilder();
		for (int i = 0; (i + result.length()) < length; i++) {
			buf.append('0');
		}
		buf.append(result);
		return buf.toString();
	}
	
	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @return String binary representation. 
	 */
	public static String toBinaryString(byte value) {
		return toBinaryString((value & 0xFF), 8);
	}
	
	/**
	 * Helper. Returns a String of the binary representation of the given value. 
	 * @param value to convert the value
	 * @return String binary representation. 
	 */
	public static String toBinaryString(short value) {
		return toBinaryString((value & 0xFFFF), 16);
	}

}
