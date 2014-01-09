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
package net.utp4j.channels.impl.log;

/**
 * Interface to the data logger. If {@see UtpAlgConfiguration.DEBUG} true, the real logger will be loaded ({@see UtpDataLogger}). if false, {@see UtpNopLogger}
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
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