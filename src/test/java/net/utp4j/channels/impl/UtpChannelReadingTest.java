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

import net.utp4j.channels.UtpSocketState;
import net.utp4j.channels.impl.alg.UtpAlgConfiguration;
import net.utp4j.data.MicroSecondsTimeStamp;
import net.utp4j.data.UtpHeaderExtension;
import net.utp4j.data.UtpPacket;
import net.utp4j.data.UtpPacketUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import static net.utp4j.data.bytes.BinaryToStringTestHelper.toBinaryString;
import static net.utp4j.data.bytes.UnsignedTypesUtil.longToUbyte;
import static net.utp4j.data.bytes.UnsignedTypesUtil.longToUshort;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class UtpChannelReadingTest {

    @Test
    public void test() throws InterruptedException {

        UtpAlgConfiguration.AUTO_ACK_SMALLER_THAN_ACK_NUMBER = false;
        UtpAlgConfiguration.SKIP_PACKETS_UNTIL_ACK = 1;

        //mocking stuff
        UtpSocketChannelImpl channel = new UtpSocketChannelImpl();
        channel.setState(UtpSocketState.CONNECTED);
        DatagramSocket socket = mock(DatagramSocket.class);

        MicroSecondsTimeStamp stamp = mock(MicroSecondsTimeStamp.class);
        when(stamp.utpDifference(anyInt(), anyInt())).thenReturn(500000000);
        when(stamp.timeStamp()).thenReturn(10000000000000L);
        when(stamp.utpTimeStamp()).thenReturn(1251241241);

        channel.setTimetamper(stamp);
        channel.setDgSocket(socket);
        channel.setRemoteAddress(new InetSocketAddress("localhost", 12345));

        // last recieved packet has seqNr. 2, next one will be packet with seqNr. 3
        channel.setAckNumber(2);

        /*
         * argument captor on socket, which will record all invocations of socket.send(packet)
         * and capture the arguments, in this case the arguments are ack packets.
         */
        ArgumentCaptor<DatagramPacket> ackOne = ArgumentCaptor.forClass(DatagramPacket.class);

        try {
            // order of recieving the data packets -> 3,4,6,8,5, 7
            channel.recievePacket(createPacket(3)); // ack 3
            channel.recievePacket(createPacket(4)); // ack 4
            channel.recievePacket(createPacket(6)); // ack 4, Sack 6 -> 00000001
            channel.recievePacket(createPacket(8)); // ack 4, Sack 6, 8 -> 00000101
            channel.recievePacket(createPacket(5)); // ack 6, sack 8 -> 00000001
            channel.recievePacket(createPacket(7)); // ack 8

            // allocating reading buffer and start test.
            ByteBuffer buffer = ByteBuffer.allocate(30000);
            channel.read(buffer);

            // wait for read to finish
            Thread.sleep(1000);

            // verify 6 ack packets where send and capture them
            verify(socket, times(6)).send(ackOne.capture());
            List<DatagramPacket> allValues = ackOne.getAllValues();
            Iterator<DatagramPacket> iterator = allValues.iterator();

            // extract utp packets from the udp packets.
            UtpPacket three = UtpPacketUtils.extractUtpPacket(iterator.next());
            UtpPacket four = UtpPacketUtils.extractUtpPacket(iterator.next());
            UtpPacket six = UtpPacketUtils.extractUtpPacket(iterator.next());
            UtpPacket eight = UtpPacketUtils.extractUtpPacket(iterator.next());
            UtpPacket five = UtpPacketUtils.extractUtpPacket(iterator.next());
            UtpPacket seven = UtpPacketUtils.extractUtpPacket(iterator.next());


            // first two packets were acked normally
            testPacket(three, 3, null, UtpPacketUtils.STATE);
            testPacket(four, 4, null, UtpPacketUtils.STATE);

            // packet six recieved before packet 5: [Ack:4; SACK:6]
            String selAckSix = "00000001" + "00000000" + "00000000" + "00000000";
            testPacket(six, 4, selAckSix, UtpPacketUtils.STATE);

            // packet 8 recieved before packet 5: [Ack: 4; SACK:6,8]
            String selAckeight = "00000101" + "00000000" + "00000000" + "00000000";
            testPacket(eight, 4, selAckeight, UtpPacketUtils.STATE);

            // packet 5 recieved, so everything recieved up to packet 6: [Ack:6, SACK:8]
            String selAckfive = "00000001" + "00000000" + "00000000" + "00000000";
            testPacket(five, 6, selAckfive, UtpPacketUtils.STATE);

            // everything recieved up till packet 8, means job done: [Ack:8]
            testPacket(seven, 8, null, UtpPacketUtils.STATE);


            channel.close();
            buffer.flip();

            // buffer should have 6'000 bytes in it
            assertEquals(6000, buffer.limit());

            // read from buffer
            byte[] third = new byte[1000];
            buffer.get(third);
            byte[] fourth = new byte[1000];
            buffer.get(fourth);
            byte[] fifth = new byte[1000];
            buffer.get(fifth);
            byte[] sixt = new byte[1000];
            buffer.get(sixt);
            byte[] seventh = new byte[1000];
            buffer.get(seventh);
            byte[] eighth = new byte[1000];
            buffer.get(eighth);

            /*
             *  test recieved data. first 1000 bytes each should == 3,
             *  second 1000 bytes each should == 4
             *  etc.
             */
            assertArrayEquals(getPayload(3), third);
            assertArrayEquals(getPayload(4), fourth);
            assertArrayEquals(getPayload(5), fifth);
            assertArrayEquals(getPayload(6), sixt);
            assertArrayEquals(getPayload(7), seventh);
            assertArrayEquals(getPayload(8), eighth);

            /*
             * check buffer is now empty
             */
            assertFalse(buffer.hasRemaining());


        } catch (IOException e) {
            // expecting no exception.
            fail("Exception occured but was not expected");
            e.printStackTrace();
        }
    }

    private void testPacket(UtpPacket pkt, int seq, String selAck, byte type) {

        assertEquals(longToUshort(seq), pkt.getAckNumber());
        assertEquals(type, pkt.getTypeVersion());

        if (selAck != null) {
            UtpHeaderExtension[] ext = pkt.getExtensions();
            if (ext == null || ext.length != 1) {
                fail("expecting selective ack extension, but got none here or more than 1");
            }
            UtpHeaderExtension selectiveAck = ext[0];
            assertEquals(longToUbyte(0), selectiveAck.getNextExtension());
            assertEquals(longToUbyte(4), selectiveAck.getLength());
            String selAckString = toBinaryString(selectiveAck.getBitMask()[0]) + toBinaryString(selectiveAck.getBitMask()[1])
                    + toBinaryString(selectiveAck.getBitMask()[2]) + toBinaryString(selectiveAck.getBitMask()[3]);
            assertEquals(selAck, selAckString);
        }


    }

    public DatagramPacket createPacket(int seqNumber) {
        UtpPacket utpPacket = new UtpPacket();
        utpPacket.setSequenceNumber(longToUshort(seqNumber));
        utpPacket.setTypeVersion(UtpPacketUtils.DATA);
        utpPacket.setPayload(getPayload(seqNumber));
        utpPacket.setWindowSize(1);
        byte[] array = utpPacket.toByteArray();
        return new DatagramPacket(array, array.length);
    }

    private byte[] getPayload(int seqNumber) {
        byte[] array = new byte[1000];
        for (int i = 0; i < 1000; i++) {
            array[i] = (byte) seqNumber;
        }
        return array;
    }

}
