package ch.uzh.csg.utp4j.data;

import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUbyte;

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
	
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} 
		if (!(obj instanceof SelectiveAckHeaderExtension)) {
			return false;
		} else {
			byte[] mine = toByteArray();
			byte[] their = toByteArray();
			if (their.length != mine.length) {
				return false;
			}
			for (int i = 0; i < mine.length; i++) {
				if (mine[i] != their[i]) {
					return false;
				}
			}
			return true;
		}
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
