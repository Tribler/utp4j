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
package net.utp4j.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MicroSecondsTimeStampTest {

	@Test
	public void test() throws InterruptedException {
		
		MicroSecondsTimeStamp stamp1 = new MicroSecondsTimeStamp();
		MicroSecondsTimeStamp stamp2 = new MicroSecondsTimeStamp();
		
		long stamp1Ms = stamp1.timeStamp();
		
		long stampedMilli = stamp1Ms/1000;
		long currentMilli = System.currentTimeMillis();
			
		assertTrue((currentMilli - stampedMilli) < 100, "Expecting timestamp()/1000 and System.currentTimeMillis() " +
						"to have less than 100 milliseconds difference");
		
		assertTrue((currentMilli - stampedMilli) >= 0, "Expecting timestamp()/1000 and System.currentTimeMillis() " +
						"to have non negative difference");
		
		long stamp2Ms = stamp2.timeStamp();

		assertTrue(stamp2Ms - stamp1Ms > 0, "Expecting stamp1 to be older than stamp2 and not equal");
		
	}
}
