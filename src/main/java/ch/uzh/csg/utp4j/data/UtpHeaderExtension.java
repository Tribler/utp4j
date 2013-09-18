package ch.uzh.csg.utp4j.data;
import static ch.uzh.csg.utp4j.data.bytes.UnsignedTypesUtil.longToUbyte;

public abstract class UtpHeaderExtension {
	
	public static UtpHeaderExtension resolve(byte b) {
		if (b == longToUbyte(1)) {
			return new SelectiveAckHeaderExtension();
		} else {
			return null;
		}
	}

	public abstract byte getNextExtension();
	public abstract void setNextExtension(byte nextExtension) ;
	
	public abstract byte getLength();
	
	public abstract byte[] getBitMask();
	public abstract void setBitMask(byte[] bitMask);
	
	public abstract byte[] toByteArray();

}
