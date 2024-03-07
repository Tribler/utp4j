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

	void currentWindow(int currentWindow);

	void ourDifference(long difference);

	void minDelay(long minDelay);

	void ourDelay(long ourDelay);

	void offTarget(long offTarget);

	void delayFactor(double delayFactor);

	void windowFactor(double windowFactor);

	void gain(int gain);

	void ackRecieved(int seqNrToAck);

	void sAck(int sackSeqNr);

	void next();

	void maxWindow(int maxWindow);

	void end(int bytesLength);

	void microSecTimeStamp(long logTimeStampMillisec);

	void pktRtt(long packetRtt);

	void rttVar(long rttVar);

	void rtt(long rtt);

	void advertisedWindow(int advertisedWindowSize);

	void theirDifference(int theirDifference);

	void theirMinDelay(long theirMinDelay);

	void bytesSend(int bytesSend);

}