/**
 * 	This file is part of Kayak.
 *
 *	Kayak is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU Lesser General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *
 *	Kayak is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public License
 *	along with Kayak.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.github.kayak.core;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A RAWConnection extends the {@link SocketcandConnection} and adds methods
 * that bring a socketcand in BCM mode. Frames are delivered asynchronously
 * through an own thread.
 * @author Jan-Niklas Meier <dschanoeh@googlemail.com>
 *
 */
public class BCMConnection extends SocketcandConnection {

    private static final Logger logger = Logger.getLogger(BCMConnection.class.getName());

    private Socket socket;
    private OutputStreamWriter output;
    private Thread thread;
    private InputStreamReader input;
    private Boolean connected = false;

    private Runnable runnable = new Runnable() {

        @Override
        public void run() {
            StringBuilder sb = new StringBuilder(40);

            while (true) {
                if(Thread.interrupted())
                    return;

                try {
                    String frame = getElement();

                    String[] fields = frame.split("\\s");

                    /* We received a frame */
                    if (fields[1].equals("frame")) {
                        try {
                            sb.setLength(0);
                            for (int i = 4; i < fields.length-1; i++) {
                                sb.append(fields[i]);
                            }
                            Frame f;

                            if(fields[2].length() <= 3) {
                                f = new Frame(Integer.valueOf(fields[2], 16), false, Util.hexStringToByteArray(sb.toString()));
                            } else {
                                f = new Frame(Integer.valueOf(fields[2], 16), true, Util.hexStringToByteArray(sb.toString()));
                            }

                            int pos = 0;
                            for(;pos<fields[3].length();pos++) {
                                if(fields[3].charAt(pos) =='.')
                                    break;
                            }
                            long timestamp = 1000000 * Long.parseLong(fields[3].substring(0, pos)) + Long.parseLong(fields[3].substring(pos+1));
                            f.setTimestamp(timestamp);
                            FrameListener receiver = getListener();
                            if (receiver != null) {
                                receiver.newFrame(f);
                            }

                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Could not properly deliver CAN frame", ex);
                        }
                    } else if (fields[1].equals("error")) {
                        logger.log(Level.WARNING, "Received error from socketcand: {0}", frame);
                    }
                } catch(InterruptedException ex) {
                    logger.log(Level.WARNING, "Interrupted exception. Shutting down connection thread");
                    return;
                } catch (IOException ex) {
                    /*
                     * A read from the socket may time out if there are very few frames.
                     * this will cause an IOException. This is ok so we will ignore these
                     * exceptions
                     */
                }
            }
        }
    };

    public Boolean isConnected() {
        return connected;
    }

    public BCMConnection(BusURL url) {
        this.host = url.getHost();
        this.port = url.getPort();
        this.busName = url.getBus();
    }

    public void open() {
        InetSocketAddress address = new InetSocketAddress(host, port);

        try {
            socket = new Socket();
            socket.connect(address);
            socket.setSoTimeout(1000);
            socket.setTcpNoDelay(Boolean.TRUE);

            input = new InputStreamReader(socket.getInputStream(), "ASCII");
            setInput(input);
            output = new OutputStreamWriter(socket.getOutputStream(), "ASCII");

            String ret = getElement();
            if (!ret.equals("< hi >")) {
                logger.log(Level.SEVERE, "Did not receive greeting from host.");
                return;
            }

            output.write("< open " + busName + " >");
            output.flush();

            ret = getElement();
            if (!ret.equals("< ok >")) {
                logger.log(Level.SEVERE, "Could not open bus");
                return;
            }
            socket.setSoTimeout(100);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "IOException while creating the socket.", e);
            return;
        }

        /* Start worker thread for frame reception */
        thread = new Thread(runnable);
        thread.setName("BCMConnection thread");
        thread.start();
        connected = true;
    }

    public void close() {
        if (thread != null && thread.isAlive()) {
            try {
                thread.interrupt();
                thread.join();
            } catch (Exception e) {
            }
        }

        try {
            socket.close();
        } catch (IOException e) {
        }
        connected = false;
    }

    private synchronized void send(String s) {
        try {
            output.write(s);
            output.flush();
        } catch(IOException ex) {
            logger.log(Level.WARNING,"IOException while sending data.", ex);
        }
    }

    public void subscribeTo(int id, boolean extended, int sec, int usec) {
        StringBuilder sb = new StringBuilder(30);
        sb.append("< subscribe ");
        sb.append(String.valueOf(sec));
        sb.append(' ');
        sb.append(String.valueOf(usec));
        sb.append(' ');
        if(extended) {
            sb.append(String.format("%08x", id));
        } else {
            sb.append(String.format("%03x", id));
        }
        sb.append(" >");
        send(sb.toString());
    }

    public void unsubscribeFrom(int id, boolean extended) {
        StringBuilder sb = new StringBuilder(30);
        sb.append("< unsubscribe ");
        if(extended) {
            sb.append(String.format("%08x", id));
        } else {
            sb.append(String.format("%03x", id));
        }
        sb.append(" >");
        send(sb.toString());
    }

    public void addSendJob(int id, boolean extended, byte[]data, int sec, int usec) {
        StringBuilder sb = new StringBuilder(40);
        sb.append("< add ");
        sb.append(Integer.toString(sec));
        sb.append(' ');
        sb.append(Integer.toString(usec));
        sb.append(' ');
        if(extended) {
            sb.append(String.format("%08x", id));
        } else {
            sb.append(String.format("%03x", id));
        }
        sb.append(' ');
        sb.append(Integer.toString(data.length));
        sb.append(' ');
        sb.append(Util.byteArrayToHexString(data, true));
        sb.append(" >");
        send(sb.toString());
    }

    public void removeSendJob(int id, boolean extended) {
        StringBuilder sb = new StringBuilder(40);
        sb.append("< delete ");
        if(extended) {
            sb.append(String.format("%08x", id));
        } else {
            sb.append(String.format("%03x", id));
        }
        sb.append(" >");
        send(sb.toString());
    }

    public void sendFrame(Frame f) {
        StringBuilder sb = new StringBuilder(50);
        sb.append("< send ");
        if(f.isExtended()) {
            sb.append(String.format("%08x", f.getIdentifier()));
        } else {
            sb.append(String.format("%03x", f.getIdentifier()));
        }
        sb.append(' ');
        sb.append(Integer.toString(f.getLength()));
        sb.append(' ');
        sb.append(Util.byteArrayToHexString(f.getData(), true));
        sb.append(" >");
        send(sb.toString());
    }

}
