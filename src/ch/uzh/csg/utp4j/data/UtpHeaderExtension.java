package ch.uzh.csg.utp4j.data;

public interface UtpHeaderExtension {

	public byte getNextExtension();
	public void setNextExtension(byte nextExtension) ;
	
	public byte getLength();
	
	public byte[] getBitMask();
	public void setBitMask(byte[] bitMask);
	
	public byte[] toByteArray();

}
