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
package net.utp4j.examples.configtest;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.sun.management.OperatingSystemMXBean;

@SuppressWarnings("restriction")
public class CpuLoadMeasure implements Runnable {

	private OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
	
	private volatile ArrayList<Double> measurements = new ArrayList<>();
	private volatile Semaphore semaphore = new Semaphore(1);

	
	
	@Override
	public void run() {
		try {
			semaphore.acquire();
			double processCpu = osBean.getProcessCpuLoad();
			// returns -1 if not enough data is available. 
			// -1 corrupts our measurement
			if (processCpu > 0) {
				measurements.add(processCpu);				
			}
			semaphore.release();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	public void reset() {
		try {
			semaphore.acquire();
			measurements.clear();
			semaphore.release();
		} catch (InterruptedException e) {
			measurements.clear();
			e.printStackTrace();
		}
	}
	
	public double getAverageCpu() {
		try {
			semaphore.acquire();
			double cpu = average();
			semaphore.release();
			return cpu;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return -1;
		}
	}

	private double average() {
		double total = 0;
		double length = measurements.size();
		if (measurements.size() == 0) {
			return -1;
		}
		for (Double sample : measurements) {
			total += sample;
		}
		return total/length;
	}

}
