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

import net.utp4j.channels.UtpServerSocketChannel;
import net.utp4j.channels.UtpSocketChannel;
import net.utp4j.channels.UtpSocketState;
import net.utp4j.channels.exception.CannotCloseServerException;
import net.utp4j.channels.futures.UtpAcceptFuture;
import net.utp4j.channels.impl.accept.UtpAcceptFutureImpl;
import net.utp4j.channels.impl.recieve.ConnectionIdTriplet;
import net.utp4j.channels.impl.recieve.UtpPacketRecievable;
import net.utp4j.channels.impl.recieve.UtpRecieveRunnable;
import net.utp4j.data.UtpPacket;
import net.utp4j.data.UtpPacketUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class UtpServerSocketChannelImpl extends UtpServerSocketChannel implements UtpPacketRecievable {

    private UtpRecieveRunnable listenRunnable;
    private final Queue<UtpAcceptFutureImpl> acceptQueue = new LinkedList<UtpAcceptFutureImpl>();
    private final Map<Integer, ConnectionIdTriplet> connectionIds = new HashMap<Integer, ConnectionIdTriplet>();
    private boolean listenRunnerStarted = false;


    /*
     * implements accept.
     */
    @Override
    protected UtpAcceptFuture acceptImpl() {

        if (!listenRunnerStarted) {
            Thread listenRunner = new Thread(getListenRunnable(),
                    "listenRunnable_" + getSocket().getLocalPort());
            listenRunner.start();
            listenRunnerStarted = true;
        }

        UtpAcceptFutureImpl future;
        try {
            future = new UtpAcceptFutureImpl();
            acceptQueue.add(future);
            return future;
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    /*
     * handles syn packet.
     */
    private void synRecieved(DatagramPacket packet) {
        if (handleDoubleSyn(packet)) {
            return;
        }
        if (packet != null && acceptQueue.peek() != null) {
            boolean registered = false;
            UtpAcceptFutureImpl future = acceptQueue.poll();
            UtpSocketChannelImpl utpChannel = null;
            try {
                utpChannel = (UtpSocketChannelImpl) UtpSocketChannel.open();
                utpChannel.setDgSocket(getSocket());
                utpChannel.recievePacket(packet);
                utpChannel.setServer(this);
                registered = registerChannel(utpChannel);
            } catch (IOException e) {
                future.setIOException(e);
            }

            /* Collision in Connection ids or failed to ack.
             * Ignore Syn Packet and let other side handle the issue. */
            if (!registered) {
                utpChannel = null;
            }
            future.synRecieved(utpChannel);
        }
    }

    /*
     * handles double syn....
     */
    private boolean handleDoubleSyn(DatagramPacket packet) {
        UtpPacket pkt = UtpPacketUtils.extractUtpPacket(packet);
        int connId = pkt.getConnectionId();
        connId = (connId & 0xFFFF) + 1;
        ConnectionIdTriplet triplet = connectionIds.get(connId);
        if (triplet != null) {
            triplet.getChannel().recievePacket(packet);
            return true;
        }

        return false;
    }

    /*
     * handles the recieving of a pkt. if is a syn packet, the server takes care of it, otherwise it will be passed to the channel.
     */
    @Override
    public void recievePacket(DatagramPacket packet) {
        if (UtpPacketUtils.isSynPkt(packet)) {
            synRecieved(packet);
        } else {
            UtpPacket utpPacket = UtpPacketUtils.extractUtpPacket(packet);
            ConnectionIdTriplet triplet = connectionIds.get(utpPacket.getConnectionId() & 0xFFFF);
            if (triplet != null) {
                triplet.getChannel().recievePacket(packet);
            }
        }
    }

    protected UtpRecieveRunnable getListenRunnable() {
        return listenRunnable;
    }

    public void setListenRunnable(UtpRecieveRunnable listenRunnable) {
        this.listenRunnable = listenRunnable;
    }

    /*
     * registers a channel.
     */
    private boolean registerChannel(UtpSocketChannelImpl channel) {
        ConnectionIdTriplet triplet = new ConnectionIdTriplet(
                channel, channel.getConnectionIdRecieving(), channel.getConnectionIdsending());

        if (isChannelRegistrationNecessary(channel)) {
            connectionIds.put((int) (channel.getConnectionIdRecieving() & 0xFFFF), triplet);
            return true;
        }

        /* Connection id collision found or not been able to ack.
         *  ignore this syn packet */
        return false;
    }

    /*
     * true if channel reg. is required.
     */
    private boolean isChannelRegistrationNecessary(UtpSocketChannelImpl channel) {
        return connectionIds.get(channel.getConnectionIdRecieving()) == null
                && channel.getState() != UtpSocketState.SYN_ACKING_FAILED;
    }

    /**
     * closes this server.
     */
    @Override
    public void close() {
        if (connectionIds.isEmpty()) {
            listenRunnable.graceFullInterrupt();
        } else {
            throw new CannotCloseServerException(connectionIds.values());
        }
    }

    /**
     * Unregisters the channel.
     *
     * @param utpSocketChannelImpl
     */
    public void unregister(UtpSocketChannelImpl utpSocketChannelImpl) {
        connectionIds.remove((int) utpSocketChannelImpl.getConnectionIdRecieving() & 0xFFFF);
    }

}
