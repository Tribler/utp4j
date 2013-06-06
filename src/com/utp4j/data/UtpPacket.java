package com.utp4j.data;

import java.util.Random;

import static com.utp4j.data.UtpPacketUtils.*;
import static com.utp4j.data.bytes.UnsignedTypesUtil.*;



/**
 * uTP Package
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public class UtpPacket {
	
	private byte typeVersion;
	private byte firstExtension;
	private short connectionId;
	private int timestamp;
	private int timestampDifference;
	private int windowSize;
	private short sequenceNumber;
	private short ackNumber;
	private UtpHeaderExtension[] extensions;
	private byte[] payload;
	
	public static int DEF_HEADER_LENGTH = 20;
	
	public byte[] getPayload() {
		return payload;
	}
	
	public void setPayload(byte[] payload) {
		this.payload = payload;
	}
	
	public void setExtensions(UtpHeaderExtension[] extensions) {
		this.extensions = extensions;
	}
	
	public UtpHeaderExtension[] getExtensions() {
		return this.extensions;
	}
	
	public byte getFirstExtension() {
		return firstExtension;
	}
	
	public void setFirstExtension(byte firstExtension) {
		this.firstExtension = firstExtension;
	}
	
	public byte getTypeVersion() {
		return typeVersion;
	}

	public void setTypeVersion(byte typeVersion) {
		this.typeVersion = typeVersion;
	}

	public short getConnectionId() {
		return connectionId;
	}

	public void setConnectionId(short connectionId) {
		this.connectionId = connectionId;
	}

	public int getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(int timestamp) {
		this.timestamp = timestamp;
	}

	public int getTimestampDifference() {
		return timestampDifference;
	}

	public void setTimestampDifference(int timestampDifference) {
		this.timestampDifference = timestampDifference;
	}

	public int getWindowSize() {
		return windowSize;
	}

	public void setWindowSize(int windowSize) {
		this.windowSize = windowSize;
	}

	public short getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(short sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public short getAckNumber() {
		return ackNumber;
	}

	public void setAckNumber(short ackNumber) {
		this.ackNumber = ackNumber;
	}

	
	public byte[] toByteArray() {
		
		if (!hasExtensions()) {
			return joinByteArray(getExtensionlessByteArray(), payload);
		}
		

		int offset = DEF_HEADER_LENGTH;
		int headerLength = offset + getTotalLengthOfExtensions();
		byte[] header = new byte[headerLength];
		byte[] extensionlessArray = getExtensionlessByteArray();
		
		for (int i = 0; i< extensionlessArray.length; i++) {
			header[i] = extensionlessArray[i];
		}
		
		for (int i = 0; i < extensions.length; i++) {
			byte[] extenionBytes = extensions[i].toByteArray();
			for (int j = 0; j < extenionBytes.length; j++) {
				header[offset++] = extenionBytes[j];
			}
		}
		return joinByteArray(header, payload);
		
	}

	private byte[] getExtensionlessByteArray() {
		byte[] array = {typeVersion, firstExtension, (byte) (connectionId >> 8), (byte) (connectionId),
		(byte) (timestamp >> 24), (byte) (timestamp >> 16), (byte) (timestamp >> 8), (byte) (timestamp),
		(byte) (timestampDifference >> 24), (byte) (timestampDifference >> 16), (byte) (timestampDifference >> 8), (byte) (timestampDifference),
		(byte) (windowSize >> 24), (byte) (windowSize >> 16), (byte) (windowSize >> 8), (byte) (windowSize),
		(byte) (sequenceNumber >> 8), (byte) (sequenceNumber), (byte) (ackNumber >> 8), (byte) (ackNumber), };
		return array;
	}

	private boolean hasExtensions() {
		return !((extensions == null || extensions.length == 0) && firstExtension == 0);
	}

	private int getTotalLengthOfExtensions() {
		if (!hasExtensions()) {
			return 0;
		}
		int length = 0;
		for (int i = 0; i < extensions.length; i++) {
			length += 2 + extensions[i].getBitMask().length;
		}
		return length;
	}
	
	public int getPacketLength() {
		byte[] pl = getPayload();
		int plLength = pl != null ? pl.length : 0;
		return DEF_HEADER_LENGTH + getTotalLengthOfExtensions() + plLength;
	}
	
}
