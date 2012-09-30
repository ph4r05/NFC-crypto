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

    private final static byte MY_SIGNATURE = 1;
    private byte[] echoBytes;
    private static final short LENGTH_ECHO_BYTES = 256;
    private final static byte INS_INIT = 0x01;
    private final static byte INS_SIGN = 0x02;
    private final static byte INS_MSISDN = 0x04;
    private final static byte INS_ECHO = 0x08;
    private final static byte INS_CODE = 0x10;
    private byte[] msisdn;
    private byte[] opres;
    private byte[] key;
    private boolean initialized = false;
    private byte myCode = MY_SIGNATURE;

    /**
     * Only this class's install method should create the applet object.
     */
    protected HelloWorld() {
        echoBytes = new byte[LENGTH_ECHO_BYTES];
        msisdn = new byte[16];
        opres = new byte[4];
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

    public static short readShort(byte[] data, short offset) {
	return (short) (((data[(short)(offset)] << 8)) | ((data[(short)(offset + 1)] & 0xff)));
    }

    public static byte[] shortToByteArray(short s) {
	return new byte[] { (byte) ((s & (short)0xFF00) >> 8), (byte) (s & (short)0x00FF) };
    }
    
    public static void shortToExistingByteArray(short s, byte[] buff, short offset) {
    	buff[(short)(offset)]   = (byte)((s & (short)0xFF00) >> 8);
    	buff[(short)(offset+1)] =  (byte) (s & (short)0x00FF);
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
        short bytesRead = apdu.setIncomingAndReceive();
        short echoOffset = (short) 0;
        while (bytesRead > 0) {
            Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, echoBytes, echoOffset, bytesRead);
            echoOffset += bytesRead;
            bytesRead = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }
                
        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_MSISDN:
                // create a byte array out of the value (length: 1)
                msisdn[0] = myCode;

                apdu.setOutgoing();
                apdu.setOutgoingLength((byte) msisdn.length);
                apdu.sendBytesLong(msisdn, (short) 0, // offset
                        //(byte) msisdn.length); // length
                        (byte) 1); // length
                break;
            case INS_CODE:
                myCode=buf[ISO7816.OFFSET_CDATA];
                
                // create a byte array out of the value (length: 1)
                msisdn[0] = myCode;

                apdu.setOutgoing();
                apdu.setOutgoingLength((byte) msisdn.length);
                apdu.sendBytesLong(msisdn, (short) 0, // offset
                        //(byte) msisdn.length); // length
                        (byte) 1); // length
                break;
            case INS_SIGN:
                // perform operation on two bytes received
                if (echoOffset>=4){
                    short num1 = readShort(buf, (short)(ISO7816.OFFSET_CDATA));
                    short num2 = readShort(buf, (short)(ISO7816.OFFSET_CDATA+2));
                    short res  = (short) (MY_SIGNATURE == 1 ? num1*num2 : num1+num2);
                    shortToExistingByteArray(res, opres, (short)0);
                    
                    apdu.setOutgoing();
                    apdu.setOutgoingLength((short) (2));
                    apdu.sendBytesLong(opres, (short) 0, (short) 2);
                }
                break;

            case INS_ECHO:
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
