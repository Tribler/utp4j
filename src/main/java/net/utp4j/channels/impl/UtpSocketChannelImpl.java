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
package net.utp4j.channels.impl;

import net.utp4j.channels.UtpSocketChannel;
import net.utp4j.channels.UtpSocketState;
import net.utp4j.channels.futures.UtpCloseFuture;
import net.utp4j.channels.futures.UtpWriteFuture;
import net.utp4j.channels.impl.alg.UtpAlgConfiguration;
import net.utp4j.channels.impl.conn.ConnectionTimeOutRunnable;
import net.utp4j.channels.impl.conn.UtpConnectFutureImpl;
import net.utp4j.channels.impl.read.UtpReadFutureImpl;
import net.utp4j.channels.impl.read.UtpReadingRunnable;
import net.utp4j.channels.impl.recieve.UtpPacketRecievable;
import net.utp4j.channels.impl.recieve.UtpRecieveRunnable;
import net.utp4j.channels.impl.write.UtpWriteFutureImpl;
import net.utp4j.channels.impl.write.UtpWritingRunnable;
import net.utp4j.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.*;

import static net.utp4j.channels.UtpSocketState.*;
import static net.utp4j.data.UtpPacketUtils.*;
import static net.utp4j.data.bytes.UnsignedTypesUtil.*;

/**
 * Implements and hides implementation details from the superclass.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public class UtpSocketChannelImpl extends UtpSocketChannel implements
        UtpPacketRecievable {

    private final BlockingQueue<UtpTimestampedPacketDTO> queue = new LinkedBlockingQueue<UtpTimestampedPacketDTO>();

    private UtpRecieveRunnable reciever;
    private UtpWritingRunnable writer;
    private UtpReadingRunnable reader;
    private final Object sendLock = new Object();

    private UtpServerSocketChannelImpl server;
    private ScheduledExecutorService retryConnectionTimeScheduler;
    private int connectionAttempts = 0;

    private int eofPacket;


    private static final Logger log = LoggerFactory
            .getLogger(UtpSocketChannelImpl.class);

    /*
     * Handles packet.
     */
    @Override
    public void recievePacket(DatagramPacket udpPacket) {


        if (isSynAckPacket(udpPacket)) {
            handleSynAckPacket(udpPacket);
        } else if (isResetPacket(udpPacket)) {
            handleResetPacket(udpPacket);
        } else if (isSynPkt(udpPacket)) {
            handleIncommingConnectionRequest(udpPacket);
        } else if (isDataPacket(udpPacket)) {
            handlePacket(udpPacket);
        } else if (isStatePacket(udpPacket)) {
            handlePacket(udpPacket);
        } else if (isFinPacket(udpPacket)) {
            handleFinPacket(udpPacket);
        } else {
            sendResetPacket(udpPacket.getSocketAddress());
        }

    }

    private void handleFinPacket(DatagramPacket udpPacket) {
        stateLock.lock();
        try {
            setState(UtpSocketState.GOT_FIN);
            UtpPacket finPacket = extractUtpPacket(udpPacket);
            long freeBuffer = 0;
            if (reader != null && reader.isRunning()) {
                freeBuffer = reader.getLeftSpaceInBuffer();
            } else {
                freeBuffer = UtpAlgConfiguration.MAX_PACKET_SIZE;
            }
            this.eofPacket = finPacket.getSequenceNumber() & 0xFFFF;
            ackPacket(finPacket, timeStamper.utpDifference(finPacket.getTimestamp()), freeBuffer);
        } catch (IOException exp) {
            exp.printStackTrace();
            // TODO: what to do if exception?
        } finally {
            stateLock.unlock();
        }
    }

    private void handleResetPacket(DatagramPacket udpPacket) {
        this.close();
    }

    private boolean isSynAckPacket(DatagramPacket udpPacket) {
        return isStatePacket(udpPacket) && getState() == UtpSocketState.SYN_SENT;
    }

    private void handleSynAckPacket(DatagramPacket udpPacket) {
        UtpPacket pkt = extractUtpPacket(udpPacket);
        if ((pkt.getConnectionId() & 0xFFFF) == getConnectionIdRecieving()) {
            stateLock.lock();
            setAckNrFromPacketSqNr(pkt);
            setState(CONNECTED);
            printState("[SynAck recieved] ");
            disableConnectionTimeOutCounter();
            connectFuture.finished(null);
            stateLock.unlock();
        } else {
            sendResetPacket(udpPacket.getSocketAddress());
        }
    }

    private boolean isSameAddressAndId(long id, SocketAddress addr) {
        return (id & 0xFFFFFFFF) == getConnectionIdRecieving()
                && addr.equals(getRemoteAdress());
    }

    private void disableConnectionTimeOutCounter() {
        if (retryConnectionTimeScheduler != null) {
            retryConnectionTimeScheduler.shutdown();
            retryConnectionTimeScheduler = null;
        }
        connectionAttempts = 0;
    }

    public int getConnectionAttempts() {
        return connectionAttempts;
    }

    public void incrementConnectionAttempts() {
        connectionAttempts++;
    }

    private void handlePacket(DatagramPacket udpPacket) {
        UtpPacket utpPacket = extractUtpPacket(udpPacket);
        queue.offer(new UtpTimestampedPacketDTO(udpPacket, utpPacket,
                timeStamper.timeStamp(), timeStamper.utpTimeStamp()));

    }

    /**
     * Sends an ack.
     *
     * @param utpPacket           the packet that should be acked
     * @param timestampDifference timestamp difference for tha ack packet
     * @param windowSize          the remaining buffer size.
     * @throws IOException
     */
    public void ackPacket(UtpPacket utpPacket, int timestampDifference,
                          long windowSize) throws IOException {
        UtpPacket ackPacket = createAckPacket(utpPacket, timestampDifference,
                windowSize);
        sendPacket(ackPacket);
    }

    /**
     * setting up a random sequence number.
     */
    public void setupRandomSeqNumber() {
        Random rnd = new Random();
        int max = (int) (MAX_USHORT - 1);
        int rndInt = rnd.nextInt(max);
        setSequenceNumber(rndInt);

    }

    private void handleIncommingConnectionRequest(DatagramPacket udpPacket) {
        UtpPacket utpPacket = extractUtpPacket(udpPacket);

        if (acceptSyn(udpPacket)) {
            int timeStamp = timeStamper.utpTimeStamp();
            setRemoteAddress(udpPacket.getSocketAddress());
            setConnectionIdsFromPacket(utpPacket);
//            setupRandomSeqNumber();
            // Fix #1 ArrayIndexOutOfBoundsException
            setSequenceNumber(DEF_SEQ_START);
            setAckNrFromPacketSqNr(utpPacket);
            printState("[Syn recieved] ");
            int timestampDifference = timeStamper.utpDifference(timeStamp,
                    utpPacket.getTimestamp());
            UtpPacket ackPacket = createAckPacket(utpPacket,
                    timestampDifference,
                    UtpAlgConfiguration.MAX_PACKET_SIZE * 1000L);
            try {
                log.debug("sending syn ack");
                sendPacket(ackPacket);
                setState(CONNECTED);
            } catch (IOException exp) {
                // TODO: In future?
                setRemoteAddress(null);
                setConnectionIdsending((short) 0);
                setConnectionIdRecieving((short) 0);
                setAckNumber(0);
                setState(SYN_ACKING_FAILED);
                exp.printStackTrace();
            }
        } else {
            sendResetPacket(udpPacket.getSocketAddress());
        }
    }

    private void sendResetPacket(SocketAddress addr) {
        log.debug("Sending RST packet");

    }

    private boolean acceptSyn(DatagramPacket udpPacket) {
        UtpPacket pkt = extractUtpPacket(udpPacket);
        return getState() == CLOSED
                || (getState() == CONNECTED && isSameAddressAndId(
                pkt.getConnectionId(), udpPacket.getSocketAddress()));
    }

    @Override
    protected void setAckNrFromPacketSqNr(UtpPacket utpPacket) {
        short ackNumberS = utpPacket.getSequenceNumber();
        setAckNumber(ackNumberS & 0xFFFF);
    }

    private void setConnectionIdsFromPacket(UtpPacket utpPacket) {
        short connId = utpPacket.getConnectionId();
        int connIdSender = (connId & 0xFFFF);
        int connIdRec = (connId & 0xFFFF) + 1;
        setConnectionIdsending(connIdSender);
        setConnectionIdRecieving(connIdRec);

    }

    @Override
    protected void connectImpl(UtpConnectFutureImpl future) {
        reciever = new UtpRecieveRunnable(getDgSocket(), this);
        reciever.start();

    }

    @Override
    protected void abortImpl() {
        if (reciever != null) {
            reciever.graceFullInterrupt();
        } else if (server != null) {
            server.unregister(this);
        }
    }

    @Override
    public UtpWriteFuture write(ByteBuffer src) {
        UtpWriteFutureImpl future = null;
        try {
            future = new UtpWriteFutureImpl();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        writer = new UtpWritingRunnable(this, src, timeStamper, future);
        writer.start();
        return future;
    }

    public BlockingQueue<UtpTimestampedPacketDTO> getDataGramQueue() {
        return queue;
    }

    /**
     * Returns a Data packet with specified header fields already set.
     *
     * @return
     */
    public UtpPacket getNextDataPacket() {
        return createDataPacket();
    }

    /**
     * Returns predefined fin packet
     *
     * @return fin packet.
     */
    public UtpPacket getFinPacket() {
        UtpPacket fin = createDataPacket();
        fin.setTypeVersion(FIN);
        return fin;
    }

    @Override
    public UtpReadFutureImpl read(ByteBuffer dst) {
        UtpReadFutureImpl readFuture = null;
        try {
            readFuture = new UtpReadFutureImpl();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        reader = new UtpReadingRunnable(this, dst, timeStamper, readFuture);
        reader.start();
        return readFuture;
    }

    /**
     * Creates an Selective Ack packet
     *
     * @param headerExtension     the header extension where the SACK data is stored
     * @param timestampDifference timestamp difference for the ack pcket.
     * @param advertisedWindow    remaining buffer size.
     * @throws IOException
     */
    public void selectiveAckPacket(SelectiveAckHeaderExtension headerExtension,
                                   int timestampDifference, long advertisedWindow) throws IOException {
        UtpPacket sack = createSelectiveAckPacket(headerExtension,
                timestampDifference, advertisedWindow);
        sendPacket(sack);

    }

    private UtpPacket createSelectiveAckPacket(SelectiveAckHeaderExtension headerExtension,
                                               int timestampDifference, long advertisedWindow) {
        UtpPacket ackPacket = new UtpPacket();
        ackPacket.setAckNumber(longToUshort(getAckNumber()));
        ackPacket.setTimestampDifference(timestampDifference);
        ackPacket.setTimestamp(timeStamper.utpTimeStamp());
        ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
        ackPacket.setWindowSize(longToUint(advertisedWindow));

        ackPacket.setFirstExtension(SELECTIVE_ACK);
        UtpHeaderExtension[] extensions = {headerExtension};
        ackPacket.setExtensions(extensions);
        ackPacket.setTypeVersion(STATE);

        return ackPacket;
    }

    @Override
    public UtpSocketState getState() {
        return state;
    }

    @Override
    public void setState(UtpSocketState state) {
        this.state = state;
    }

    @Override
    public UtpCloseFuture close() {
        abortImpl();
        if (isReading()) {
            reader.graceFullInterrupt();
        }
        if (isWriting()) {
            writer.graceFullInterrupt();
        }
        return null;
    }

    @Override
    public boolean isReading() {
        return (reader != null && reader.isRunning());
    }

    @Override
    public boolean isWriting() {
        return (writer != null && writer.isRunning());
    }

    public void ackAlreadyAcked(SelectiveAckHeaderExtension extension, int timestampDifference,
                                long windowSize) throws IOException {
        UtpPacket ackPacket = new UtpPacket();
        ackPacket.setAckNumber(longToUshort(getAckNumber()));
        SelectiveAckHeaderExtension[] extensions = {extension};
        ackPacket.setExtensions(extensions);
        ackPacket.setTimestampDifference(timestampDifference);
        ackPacket.setTimestamp(timeStamper.utpTimeStamp());
        ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
        ackPacket.setTypeVersion(STATE);
        ackPacket.setWindowSize(longToUint(windowSize));
        sendPacket(ackPacket);

    }

    @Override
    public void sendPacket(DatagramPacket pkt) throws IOException {
        synchronized (sendLock) {
            getDgSocket().send(pkt);
        }

    }

    /* general method to send a packet, will be wrapped by a UDP Packet */
    @Override
    public void sendPacket(UtpPacket packet) throws IOException {
        if (packet != null) {
            byte[] utpPacketBytes = packet.toByteArray();
            int length = packet.getPacketLength();
            DatagramPacket pkt = new DatagramPacket(utpPacketBytes, length,
                    getRemoteAdress());
            sendPacket(pkt);
        }
    }

    @Override
    public void setDgSocket(DatagramSocket dgSocket) {
        if (this.dgSocket != null) {
            this.dgSocket.close();
        }
        this.dgSocket = dgSocket;
    }

    @Override
    public void setAckNumber(int ackNumber) {
//		log.debug("ack nubmer set to: " + ackNumber);
        this.ackNumber = ackNumber;
    }

    public void setServer(UtpServerSocketChannelImpl utpServerSocketChannelImpl) {
        this.server = utpServerSocketChannelImpl;

    }

    @Override
    public void setRemoteAddress(SocketAddress remoteAdress) {
        this.remoteAddress = remoteAdress;
    }

    public void returnFromReading() {
        reader = null;
        //TODO: dispatch:
        if (!isWriting()) {
            this.state = UtpSocketState.CONNECTED;
        }
    }

    public void removeWriter() {
        writer = null;
    }

    /*
     * Start a connection time out counter which will frequently resend the syn packet.
     */
    @Override
    protected void startConnectionTimeOutCounter(UtpPacket synPacket) {
        retryConnectionTimeScheduler = Executors
                .newSingleThreadScheduledExecutor();
        ConnectionTimeOutRunnable runnable = new ConnectionTimeOutRunnable(
                synPacket, this, stateLock);
        log.debug("starting scheduler");
        // retryConnectionTimeScheduler.schedule(runnable, 2, TimeUnit.SECONDS);
        retryConnectionTimeScheduler.scheduleWithFixedDelay(runnable,
                UtpAlgConfiguration.CONNECTION_ATTEMPT_INTERVALL_MILLIS,
                UtpAlgConfiguration.CONNECTION_ATTEMPT_INTERVALL_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Called by the time out counter {@see ConnectionTimeOutRunnable}
     *
     * @param exp - is optional.
     */
    public void connectionFailed(IOException exp) {
        log.debug("got failing message");
        setSequenceNumber(DEF_SEQ_START);
        setRemoteAddress(null);
        abortImpl();
        setState(CLOSED);
        retryConnectionTimeScheduler.shutdown();
        retryConnectionTimeScheduler = null;
        connectFuture.finished(exp);

    }

    /**
     * Resends syn packet. called by {@see ConnectionTimeOutRunnable}
     *
     * @param synPacket
     */
    public void resendSynPacket(UtpPacket synPacket) {
        stateLock.lock();
        try {
            int attempts = getConnectionAttempts();
            log.debug("attempt: " + attempts);
            if (getState() == UtpSocketState.SYN_SENT) {
                try {
                    if (attempts < UtpAlgConfiguration.MAX_CONNECTION_ATTEMPTS) {
                        incrementConnectionAttempts();
                        log.debug("REATTEMPTING CONNECTION");
                        sendPacket(synPacket);
                    } else {
                        connectionFailed(new SocketTimeoutException());
                    }
                } catch (IOException e) {
                    if (attempts >= UtpAlgConfiguration.MAX_CONNECTION_ATTEMPTS) {
                        connectionFailed(e);
                    } // else ignore, try in next attempt
                }
            }
        } finally {
            stateLock.unlock();
        }

    }

    public void setTimetamper(MicroSecondsTimeStamp stamp) {
        this.timeStamper = stamp;

    }

    /*
     * Creates an ACK packet.
     */
    protected UtpPacket createAckPacket(UtpPacket pkt, int timedifference,
                                        long advertisedWindow) {
        UtpPacket ackPacket = new UtpPacket();
        if (pkt.getTypeVersion() != FIN) {
            setAckNrFromPacketSqNr(pkt);
        }
        ackPacket.setAckNumber(longToUshort(getAckNumber()));

        ackPacket.setTimestampDifference(timedifference);
        ackPacket.setTimestamp(timeStamper.utpTimeStamp());
        ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
        ackPacket.setTypeVersion(STATE);
        ackPacket.setWindowSize(longToUint(advertisedWindow));
        return ackPacket;
    }

    protected UtpPacket createDataPacket() {
        UtpPacket pkt = new UtpPacket();
        pkt.setSequenceNumber(longToUshort(getSequenceNumber()));
        incrementSequenceNumber();
        pkt.setAckNumber(longToUshort(getAckNumber()));
        pkt.setConnectionId(longToUshort(getConnectionIdsending()));
        pkt.setTimestamp(timeStamper.utpTimeStamp());
        pkt.setTypeVersion(UtpPacketUtils.DATA);
        return pkt;
    }

}
