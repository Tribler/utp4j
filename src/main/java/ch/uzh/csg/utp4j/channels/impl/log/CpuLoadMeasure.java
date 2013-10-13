package ch.uzh.csg.utp4j.channels.impl.log;

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
