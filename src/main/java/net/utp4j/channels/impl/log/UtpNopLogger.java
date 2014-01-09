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
 * AS the name states, this logger does nothing. 
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public class UtpNopLogger implements UtpStatisticLogger {

	@Override
	public void currentWindow(int currentWindow) {
	}

	@Override
	public void ourDifference(long difference) {
	}

	@Override
	public void minDelay(long minDelay) {
	}

	@Override
	public void ourDelay(long ourDelay) {
	}

	@Override
	public void offTarget(long offTarget) {
	}

	@Override
	public void delayFactor(double delayFactor) {
	}

	@Override
	public void windowFactor(double windowFactor) {
	}

	@Override
	public void gain(int gain) {
	}

	@Override
	public void ackRecieved(int seqNrToAck) {
	}

	@Override
	public void sAck(int sackSeqNr) {
	}

	@Override
	public void next() {
	}

	@Override
	public void maxWindow(int maxWindow) {
	}

	@Override
	public void end(int bytesLength) {
	}

	@Override
	public void microSecTimeStamp(long logTimeStampMillisec) {
	}

	@Override
	public void pktRtt(long packetRtt) {
	}

	@Override
	public void rttVar(long rttVar) {
	}

	@Override
	public void rtt(long rtt) {
	}

	@Override
	public void advertisedWindow(int advertisedWindowSize) {
	}

	@Override
	public void theirDifference(int theirDifference) {
	}

	@Override
	public void theirMinDelay(long theirMinDelay) {
	}

	@Override
	public void bytesSend(int bytesSend) {
	}

}
