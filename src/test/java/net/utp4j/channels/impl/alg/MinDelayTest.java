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
package net.utp4j.channels.impl.alg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MinDelayTest {

    /**
     * tests min delay.
     */
    @Test
    public void testMinDelay() {
        MinimumDelay delay = new MinimumDelay();
        delay.updateOurDelay(5, 1);
        delay.updateOurDelay(2, 2);
        delay.updateOurDelay(5, 3);
        delay.updateOurDelay(8, 12);
        assertEquals(2, delay.getCorrectedMinDelay());
    }

    /**
     * Tests the min delay, when its outdated,
     * it should be updated to the most recent.
     */
    @Test
    public void testMinDelayOutdated() {
        MinimumDelay delay = new MinimumDelay();
        // oldest minimum has timestamp 2.
        delay.updateOurDelay(5, 1);
        delay.updateOurDelay(2, 2);
        delay.updateOurDelay(5, 3);
        delay.updateOurDelay(8, 12);
        // new minimum should be 20, with timestamp MinimumDelay.MINIMUM_DIFFERENCE_TIMESTAMP_MICROSEC + x | x >= 2;
        delay.updateOurDelay(20, UtpAlgConfiguration.MINIMUM_DIFFERENCE_TIMESTAMP_MICROSEC + 2);
        assertEquals(20, delay.getCorrectedMinDelay());
    }

    /**
     * Tests clock drift correction
     */
    @Test
    public void testClockDriftCorrection() {
        MinimumDelay delay = new MinimumDelay();
        // our minDelay is 5
        delay.updateOurDelay(5, 1);
        assertEquals(5, delay.getCorrectedMinDelay());
        delay.updateTheirDelay(8, 2);
        // our delay should still be 5
        assertEquals(5, delay.getCorrectedMinDelay());

        // now remote clock ticks faster, their delay decreases
        delay.updateTheirDelay(7, 10);

        // our delay should be 6 now.
        assertEquals(6, delay.getCorrectedMinDelay());

    }

}
