package ch.uzh.csg.utp4j.examples.configtest;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

import ch.uzh.csg.utp4j.channels.UtpSocketChannel;
import ch.uzh.csg.utp4j.channels.futures.UtpConnectFuture;
import ch.uzh.csg.utp4j.channels.futures.UtpWriteFuture;
import ch.uzh.csg.utp4j.data.MicroSecondsTimeStamp;

public class ConfigTestWrite {
	private static List<String> testRunLogs = new LinkedList<String>();

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		
		String testPlan = "testPlan/testplan.csv";
		String testDataFile = "testData/sc S01E01.avi";
		
		ConfigTestPlanReader plan = new ConfigTestPlanReader(testPlan);
		plan.read();
		MicroSecondsTimeStamp timeStamper = new MicroSecondsTimeStamp();
		while(plan.hasNext()) {
			String testRunLogEntry = plan.next();
			ByteBuffer buffer = ByteBuffer.allocate(150000000);
			RandomAccessFile file     = new RandomAccessFile(testDataFile, "rw");
			FileChannel  fileChannel = file.getChannel();
			int bytesRead = 0;
			System.out.println("start reading from file");
			do {
				bytesRead = fileChannel.read(buffer);
			} while(bytesRead != -1);
			System.out.println("file read");

			UtpSocketChannel chanel = UtpSocketChannel.open();
			int bytesToSend = buffer.position();
//			UtpConnectFuture cFuture = chanel.connect(new InetSocketAddress("192.168.1.40", 13344));
			UtpConnectFuture cFuture = chanel.connect(new InetSocketAddress("localhost", 13344));
//			UtpConnectFuture cFuture = chanel.connect(new InetSocketAddress("192.168.1.44", 13344));
			
			cFuture.block();
			if (cFuture.isSuccessfull()) {
				long start = timeStamper.timeStamp();
				UtpWriteFuture writeFuture = chanel.write(buffer);
				writeFuture.block();
				if (!writeFuture.isSuccessfull()) {
					plan.failed();
					System.out.println("FAILED");
				} else {
					String logEntry = testRunLogEntry + " -- " + calculateRate(bytesToSend, start, timeStamper.timeStamp());
					System.out.println(logEntry);
					testRunLogs.add(logEntry + "\n");
				}
				System.out.println("writing test done");
			} else {
				System.out.println("FAILED");
				plan.failed();
			}
			file.close();
			fileChannel.close();
			chanel.close();
			Thread.sleep(15000);	
		}
		writeLog();
	}

	private static String calculateRate(int bytesToSend, long start, long end) {
			long seconds = (end - start)/1000000;
			long sendRate = 0;
			if (seconds != 0) {
				sendRate = (bytesToSend/1024)/seconds;
			}
			return sendRate + "kB/sec";
	}

	private static void writeLog() throws IOException {
		RandomAccessFile aFile = new RandomAccessFile("testData/auto/AutoTestLog.txt", "rw");
		FileChannel inChannel = aFile.getChannel();
		inChannel.truncate(0);
		ByteBuffer bbuffer = ByteBuffer.allocate(examineBytes());
		for (String str : testRunLogs) {
			bbuffer.put(str.getBytes());
		}
		bbuffer.flip();
		while(bbuffer.hasRemaining()) {
			inChannel.write(bbuffer);
		}
		aFile.close();
		inChannel.close();
	}

	private static int examineBytes() {
		int bytes = 0;
		for (String str : testRunLogs) {
			bytes += str.getBytes().length;
		}
		return bytes;
	}

}
