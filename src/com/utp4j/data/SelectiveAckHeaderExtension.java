package com.utp4j.data;

import static com.utp4j.data.bytes.UnsignedTypesUtil.longToUbyte;

public class SelectiveAckHeaderExtension implements UtpHeaderExtension {

	private byte nextExtension;
	private byte[] bitMask;
	
	public byte getNextExtension() {
		return nextExtension;
	}
	public void setNextExtension(byte nextExtension) {
		this.nextExtension = nextExtension;
	}
	public byte getLength() {
		return longToUbyte(bitMask.length);
	}

	public byte[] getBitMask() {
		return bitMask;
	}
	public void setBitMask(byte[] bitMask) {
		this.bitMask = bitMask;
	}
	
	public byte[] toByteArray() {
		byte[] array = new byte[2 + bitMask.length];
		array[0] = nextExtension;
		array[1] = longToUbyte(bitMask.length);
		for (int i = 0; i < bitMask.length; i++) {
			array[i + 2] = bitMask[i];
		}
		return array;
	}

	
}
