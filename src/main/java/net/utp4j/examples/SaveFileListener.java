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
package net.utp4j.examples;

import net.utp4j.channels.futures.UtpReadListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class SaveFileListener extends UtpReadListener {

    private int file;
    private volatile boolean append = false;

    public SaveFileListener(int i, boolean concat) {
        file = i;
        this.append = concat;
        createExtraThread = true;
    }

    public SaveFileListener(int i) {
        file = i;
    }

    public SaveFileListener() {
    }

    public SaveFileListener(boolean concat) {
        this.append = concat;
    }


    @Override
    public void actionAfterReading() {
        if (exception == null && byteBuffer != null) {
            try {
                byteBuffer.flip();
                File outFile = new File("testData/gotData_" + file + " .avi");
                FileOutputStream fileOutputStream = new FileOutputStream(outFile, append);
                FileChannel fchannel = fileOutputStream.getChannel();
                while (byteBuffer.hasRemaining()) {
                    fchannel.write(byteBuffer);
                }
                fchannel.close();
                fileOutputStream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if (exception != null) {
            exception.printStackTrace();
        }

    }

    @Override
    public String getThreadName() {
        return "listenerThread";
    }

}
