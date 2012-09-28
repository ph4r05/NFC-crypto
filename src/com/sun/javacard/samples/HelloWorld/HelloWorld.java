/*
 * Copyright © 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
// /*
// Workfile:@(#)HelloWorld.java	1.5
// Version:1.5
// Date:02/01/02
// 
// Archive:  /Products/Europa/samples/com/sun/javacard/samples/HelloWorld/HelloWorld.java 
// Modified:02/01/02 16:39:05
// Original author:  Mitch Butler
// */
package com.sun.javacard.samples.HelloWorld;

import javacard.framework.*;

/**
 */
public class HelloWorld extends Applet {

    private final static byte MY_SIGNATURE = 9;
    private byte[] echoBytes;
    private static final short LENGTH_ECHO_BYTES = 256;
    private final static byte INS_INIT = 0x01;
    private final static byte INS_SIGN = 0x02;
    private final static byte INS_MSISDN = 0x04;
    private final static byte INS_ECHO = 0x08;
    private byte[] msisdn;
    private byte[] key;
    private boolean initialized = false;

    /**
     * Only this class's install method should create the applet object.
     */
    protected HelloWorld() {
        echoBytes = new byte[LENGTH_ECHO_BYTES];
        register();
    }

    /**
     * Installs this applet.
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloWorld();
    }

    /**
     * Processes an incoming APDU.
     * @see APDU
     * @param apdu the incoming APDU
     * @exception ISOException with the response bytes per ISO 7816-4
     */
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_MSISDN:
                // create a byte array out of the value (length: 1)
                msisdn = new byte[1];
                msisdn[0] = MY_SIGNATURE;

                apdu.setOutgoing();
                apdu.setOutgoingLength((byte) msisdn.length);
                apdu.sendBytesLong(msisdn, (short) 0, // offset
                        (byte) msisdn.length); // length
                break;
            case INS_INIT:
                //cmdInit(apdu);
                break;
            case INS_SIGN:
                //cmdSign(apdu);
                break;

            case INS_ECHO:
                short bytesRead = apdu.setIncomingAndReceive();
                short echoOffset = (short) 0;

                while (bytesRead > 0) {
                    Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, echoBytes, echoOffset, bytesRead);
                    echoOffset += bytesRead;
                    bytesRead = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
                }

                apdu.setOutgoing();
                apdu.setOutgoingLength((short) (echoOffset + 5));

                // echo header
                apdu.sendBytes((short) 0, (short) 5);
                // echo data
                apdu.sendBytesLong(echoBytes, (short) 0, echoOffset);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
