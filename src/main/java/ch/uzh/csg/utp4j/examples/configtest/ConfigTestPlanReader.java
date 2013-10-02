package ch.uzh.csg.utp4j.examples.configtest;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

import ch.uzh.csg.utp4j.channels.impl.alg.PacketSizeModus;
import ch.uzh.csg.utp4j.channels.impl.alg.UtpAlgConfiguration;
import ch.uzh.csg.utp4j.channels.impl.log.UtpDataLogger;

public class ConfigTestPlanReader {

	private String fileLocation;
	private Deque<String> testParameters = new LinkedList<String>();
	private int testRun = 0;
	private String lastParameters;
	private SimpleDateFormat format = new SimpleDateFormat("dd_MM_hh_mm_ss");

	public ConfigTestPlanReader(String fileLocation) {
		this.fileLocation = fileLocation;
		UtpAlgConfiguration.DEBUG = false;
	}
	
	public void read() throws IOException {
		InputStream fis;
		BufferedReader br;
		String line;

		fis = new FileInputStream(fileLocation);
		br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
		boolean skipLine = true;
		while ((line = br.readLine()) != null) {
			if (!skipLine) {
				String[] split = line.split(";");
				int repetitions = Integer.parseInt(split[split.length - 1]);
				for (int i = 0; i < repetitions; i++) {
					testParameters.add(line);				
				}				
			} else {
				skipLine = false;
			}
		}
		br.close();
		br = null;
		fis = null;
	}
	
	public String next() {
		testRun++;
		Date date = new Date();
		String dateString = format.format(date);
		String logname = "auto/auto_" + dateString + "_run_" + testRun;
		UtpDataLogger.LOG_NAME = logname + ".csv";
		String parameters = testParameters.remove();
		lastParameters = parameters;
		String[] splitParameters = parameters.split(";");
		System.out.println(Arrays.toString(splitParameters));
		UtpAlgConfiguration.MINIMUM_TIMEOUT_MILLIS = Integer.parseInt(splitParameters[0]);
		UtpAlgConfiguration.PACKET_SIZE_MODE = parsePktSizeMode(splitParameters[1]);
		UtpAlgConfiguration.MAX_PACKET_SIZE = Integer.parseInt(splitParameters[2]);
		UtpAlgConfiguration.MIN_PACKET_SIZE = Integer.parseInt(splitParameters[3]);
		UtpAlgConfiguration.MINIMUM_MTU = Integer.parseInt(splitParameters[4]);
		UtpAlgConfiguration.MAX_CWND_INCREASE_PACKETS_PER_RTT = Integer.parseInt(splitParameters[5]);
		UtpAlgConfiguration.C_CONTROL_TARGET_MICROS = Integer.parseInt(splitParameters[6]);
		UtpAlgConfiguration.SEND_IN_BURST = toBool(splitParameters[7]);
		UtpAlgConfiguration.MAX_BURST_SEND = Integer.parseInt(splitParameters[8]);
		UtpAlgConfiguration.MIN_SKIP_PACKET_BEFORE_RESEND = Integer.parseInt(splitParameters[9]);
		UtpAlgConfiguration.MICROSECOND_WAIT_BETWEEN_BURSTS = Integer.parseInt(splitParameters[10]);
		UtpAlgConfiguration.TIME_WAIT_AFTER_FIN_MICROS = Integer.parseInt(splitParameters[11]);
		UtpAlgConfiguration.ONLY_POSITIVE_GAIN = toBool(splitParameters[12]);
		UtpAlgConfiguration.DEBUG = true;
		return parameters + " -- " + dateString;
		
	}
	
	private PacketSizeModus parsePktSizeMode(String strg) {
		if ("DYNAMIC_LINEAR".equals(strg)) return PacketSizeModus.DYNAMIC_LINEAR;
		else if ("CONSTANT_1472".equals(strg)) return PacketSizeModus.CONSTANT_1472;
		else if ("CONSANT_576".equals(strg)) return PacketSizeModus.CONSTANT_576;
		return null;
	}
	
	private boolean toBool(String strg) {
		return "1".equals(strg);
	}
	
	public boolean hasNext() {
		return !testParameters.isEmpty();
	}
	public void failed() {
		if (lastParameters != null) {
			testParameters.addFirst(lastParameters);			
			testRun--;
		}
	}
}
