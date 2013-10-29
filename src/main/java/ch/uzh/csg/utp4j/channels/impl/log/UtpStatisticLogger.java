package ch.uzh.csg.utp4j.channels.impl.log;

public interface UtpStatisticLogger {

	public abstract void currentWindow(int currentWindow);

	public abstract void ourDifference(long difference);

	public abstract void minDelay(long minDelay);

	public abstract void ourDelay(long ourDelay);

	public abstract void offTarget(long offTarget);

	public abstract void delayFactor(double delayFactor);

	public abstract void windowFactor(double windowFactor);

	public abstract void gain(int gain);

	public abstract void ackRecieved(int seqNrToAck);

	public abstract void sAck(int sackSeqNr);

	public abstract void next();

	public abstract void maxWindow(int maxWindow);

	public abstract void end(int bytesLength);

	public abstract void microSecTimeStamp(long logTimeStampMillisec);

	public abstract void pktRtt(long packetRtt);

	public abstract void rttVar(long rttVar);

	public abstract void rtt(long rtt);

	public abstract void advertisedWindow(int advertisedWindowSize);

	public abstract void theirDifference(int theirDifference);

	public abstract void theirMinDelay(long theirMinDelay);

	public abstract void bytesSend(int bytesSend);

}