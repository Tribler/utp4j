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
package net.utp4j.channels;

import net.utp4j.channels.futures.UtpCloseFuture;
import net.utp4j.channels.futures.UtpConnectFuture;
import net.utp4j.channels.futures.UtpReadFuture;
import net.utp4j.channels.futures.UtpWriteFuture;
import net.utp4j.channels.impl.UtpSocketChannelImpl;
import net.utp4j.channels.impl.conn.UtpConnectFutureImpl;
import net.utp4j.data.MicroSecondsTimeStamp;
import net.utp4j.data.UtpPacket;
import net.utp4j.data.UtpPacketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import static net.utp4j.channels.UtpSocketState.CLOSED;
import static net.utp4j.channels.UtpSocketState.SYN_SENT;
import static net.utp4j.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static net.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;

/**
 * Interface for a Socket
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public abstract class UtpSocketChannel {

    /* ID for outgoing packets */
    private long connectionIdSending;

    /* timestamping utility */
    protected MicroSecondsTimeStamp timeStamper = new MicroSecondsTimeStamp();

    /* current sequenceNumber */
    private int sequenceNumber;

    /* ID for incomming packets */
    private long connectionIdRecieving;

    /*
     * address of the remote sockets which this socket is connected to
     */
    protected SocketAddress remoteAddress;

    /* current ack Number */
    protected int ackNumber;

    /* reference to the underlying UDP Socket */
    protected DatagramSocket dgSocket;

    /*
     * Connection Future Object - need to hold a reference here i case
     * connection the initial connection attempt fails. So it will be updates
     * once the reattempts success.
     */
    protected UtpConnectFutureImpl connectFuture = null;

    /* lock for the socket state* */
    protected final ReentrantLock stateLock = new ReentrantLock();

    /* logger */
    private static final Logger log = LoggerFactory
            .getLogger(UtpSocketChannel.class);

    /* Current state of the socket */
    protected volatile UtpSocketState state = null;

    /* Sequencing begin */
    protected static int DEF_SEQ_START = 1;

    /**
     * Opens a new Socket and binds it to any available port
     *
     * @return {@link UtpSocketChannel}
     * @throws IOException see {@link DatagramSocket#DatagramSocket()}
     */
    public static UtpSocketChannel open() throws IOException {
        UtpSocketChannelImpl c = new UtpSocketChannelImpl();
        try {
            c.setDgSocket(new DatagramSocket());
            c.setState(CLOSED);
        } catch (IOException exp) {
            throw new IOException("Could not open UtpSocketChannel: "
                    + exp.getMessage());
        }
        return c;
    }

    /**
     * Connects this Socket to the specified address
     *
     * @param address
     * @return {@link UtpConnectFuture}
     */
    public UtpConnectFuture connect(SocketAddress address) {
        stateLock.lock();
        try {
            try {
                connectFuture = new UtpConnectFutureImpl();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
            try {
                /* underlying impl does it's connection setup */
                connectImpl(connectFuture);

                /* fill packet, set initial variables and send packet */
                setRemoteAddress(address);
                setupConnectionId();
                setSequenceNumber(DEF_SEQ_START);

                UtpPacket synPacket = UtpPacketUtils.createSynPacket();
                synPacket
                        .setConnectionId(longToUshort(getConnectionIdRecieving()));
                synPacket.setTimestamp(timeStamper.utpTimeStamp());
                sendPacket(synPacket);
                setState(SYN_SENT);
                printState("[Syn send] ");

                incrementSequenceNumber();
                startConnectionTimeOutCounter(synPacket);
            } catch (IOException exp) {
                // DO NOTHING, let's try later with reconnect runnable
            }
        } finally {
            stateLock.unlock();
        }

        return connectFuture;
    }

    /**
     * Writes the content of the Buffer to the Channel.
     *
     * @param src the buffer, it is expected to be in Reading-Mode.
     * @return {@link UtpWriteFuture} which will be updated by the channel
     */
    public abstract UtpWriteFuture write(ByteBuffer src);

    /**
     * Reads the incomming data to the channel.
     *
     * @param dst the buffer.
     * @return {@link UtpWriteFuture} which will be updated by the channel
     */
    public abstract UtpReadFuture read(ByteBuffer dst);


    /**
     * Closes the channel. Also unbinds the socket if the socket is not shared,
     * for example with the server.
     */
    public abstract UtpCloseFuture close();

    /**
     * @return The Connection ID for Outgoing Packets
     */
    public long getConnectionIdsending() {
        return connectionIdSending;
    }

    /**
     * @return true if the channel is open.
     */
    public boolean isOpen() {
        return state != CLOSED;
    }

    public abstract boolean isReading();

    public abstract boolean isWriting();

    /**
     * @return true, if this socket is connected to another socket.
     */
    public boolean isConnected() {
        return getState() == UtpSocketState.CONNECTED;
    }

    public long getConnectionIdRecieving() {
        return connectionIdRecieving;
    }

    public UtpSocketState getState() {
        return state;
    }

    public SocketAddress getRemoteAdress() {
        return remoteAddress;
    }

    public int getAckNumber() {
        return ackNumber;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public DatagramSocket getDgSocket() {
        return dgSocket;
    }

    /* signal the implementation to start the reConnect Timer */
    protected abstract void startConnectionTimeOutCounter(UtpPacket synPacket);

    /* debug method to print id's, seq# and ack# */
    protected void printState(String msg) {
        String state = "[ConnID Sending: " + connectionIdSending + "] "
                + "[ConnID Recv: " + connectionIdRecieving + "] [SeqNr. "
                + sequenceNumber + "] [AckNr: " + ackNumber + "]";
        log.debug(msg + state);

    }

    /* implementation to cancel all operations and close the socket */
    protected abstract void abortImpl();

    /* if implementation must something do during the connection */
    protected abstract void connectImpl(UtpConnectFutureImpl future);

    /*
     * increments current sequence number unlike TCP, uTp is sequencing its
     * packets, not bytes but the Seq# is only 16 bits, so overflows are likely
     * to happen Seq# == 0 not possible.
     */
    protected void incrementSequenceNumber() {
        int seqNumber = getSequenceNumber() + 1;
        if (seqNumber > MAX_USHORT) {
            seqNumber = 1;
        }
        setSequenceNumber(seqNumber);
    }

    /*
     * general methods to send packets need to be public in the impl, but also
     * need to be accessed from this class.
     */
    protected abstract void sendPacket(UtpPacket packet) throws IOException;

    protected abstract void sendPacket(DatagramPacket pkt) throws IOException;

    /*
     * sets up connection ids. incomming = rnd outgoing = incomming + 1
     */
    private void setupConnectionId() {
        Random rnd = new Random();
        int max = (int) (MAX_USHORT - 1);
        long rndInt = rnd.nextInt(max);
        setConnectionIdRecieving(rndInt);
        setConnectionIdsending(rndInt + 1);

    }


    /* set connection ID for outgoing packets */
    protected void setConnectionIdsending(long connectionIdSending) {
        this.connectionIdSending = connectionIdSending;
    }

    /**
     * Creates an Ack-Packet.
     *
     * @param pkt              the packet to be Acked
     * @param timedifference   Between t1 and t2. t1 beeing the time the remote socket has
     *                         sent this packet, t2 when this socket recieved the packet.
     *                         Differences is <b>unsigned</b> and in �s resolution.
     * @param advertisedWindow How much space this socket has in its temporary recieve buffer
     * @return ack packet
     */
    protected abstract UtpPacket createAckPacket(UtpPacket pkt, int timedifference,
                                                 long advertisedWindow);

    /*
     * setting the ack current ack number to the sequence number of the packet.
     * needs to be accessed from this class and publicly from the
     * implementation, hence protected and abstract.
     */
    protected abstract void setAckNrFromPacketSqNr(UtpPacket utpPacket);

    /*
     * creates a datapacket, increments seq. number the packet is ready to be
     * filled with data.
     */
    protected abstract UtpPacket createDataPacket();

    protected void setConnectionIdRecieving(long connectionIdRecieving) {
        this.connectionIdRecieving = connectionIdRecieving;
    }

    protected void setState(UtpSocketState state) {
        this.state = state;
    }


    protected void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    protected abstract void setRemoteAddress(SocketAddress remoteAdress);

    protected abstract void setAckNumber(int ackNumber);

    protected abstract void setDgSocket(DatagramSocket dgSocket);

}