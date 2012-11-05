package utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.tech.IsoDep;
import android.util.Log;


public class CardOperations {
	private static final String TAG="CardOperations";
	
	/**
	 * Calls SELECT operation on card - selects my applet
	 * @param isodep
	 * @return
	 * @throws IOException
	 */
	public static boolean doSelect(IsoDep isodep) throws IOException{
    	 byte[] SELECT = { (byte) 0x00, // CLA Class 
     		(byte) 0xA4, // INS Instruction 
     		(byte) 0x04, // P1 Parameter 1 
     		(byte) 0x00, // P2 Parameter 2 
     		(byte) 0x09, // Length
     		(byte) 0xa0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x0c, 0x01 
     		};
    	 
    	// select 
    	byte[] result;
    	int len;
    	
    	result = isodep.transceive(SELECT);
		len = result.length;
		Log.e(TAG, "Select, Result: " + len + "; Bytes: " + Utils.dumpByteArray(result));
		if (!(result[len-2]==(byte)0x90&&result[len-1]==(byte) 0x00))
			throw new IOException("could not select");
		return true;
    }
    
	/**
	 * Performs simple operation on card. Requests computation on card with 2 numbers, 
	 * 1 short number expected as result
	 * 
	 * @param isodep
	 * @param num1
	 * @param num2
	 * @return
	 * @throws IOException
	 */
    public static short doOperation(IsoDep isodep, short num1, short num2) throws IOException{
    	byte[] DO_OP = {
    		(byte) 0x80, // CLA Class
    		(byte) 0x02, // INS Instruction
    		(byte) 0x00, // P1 Parameter 1
    		(byte) 0x00, // P2 Parameter 2
    		(byte) 0x04, // 2 short numbers
    		(byte) 0x00, 0x00, 0x00, 0x00,
    		(byte) 0x10 // LE maximal number of bytes expected in result
    	};
    	
    	Utils.shortToExistingByteArray(num1, DO_OP, (short)5);
    	Utils.shortToExistingByteArray(num2, DO_OP, (short)7);
    	
    	// select 
    	byte[] result;
    	int len;
		
		result = isodep.transceive(DO_OP);
		len = result.length;
		Log.e(TAG, "PreOP Op: Bytes: " + Utils.dumpByteArray(DO_OP));
		Log.e(TAG, "DO Op: Result: " + len + "; Bytes: " + Utils.dumpByteArray(result));
		if (!(result[len-2]==(byte)0x90&&result[len-1]==(byte) 0x00))
			throw new IOException("could not retrieve result of operation");
		
		// convert result to short
		short resultShort = Utils.readShort(result, (short)0);
    	return resultShort;
    }
    
    /**
     * Sets new code to the card
     * @param isodep
     * @param code
     * @throws IOException
     */
    public static void doSetCode(IsoDep isodep, byte code) throws IOException{
    	byte[] DO_SL = {
    		(byte) 0x80, // CLA Class
    		(byte) 0x10, // INS Instruction
    		(byte) 0x00, // P1 Parameter 1
    		(byte) 0x00, // P2 Parameter 2
    		(byte) 0x02, // byte
    		(byte) 0x10, 0x00, 0x10
    	};
    	
    	DO_SL[5] = code;
    	
    	// select 
    	byte[] result;
    	int len;
		
		result = isodep.transceive(DO_SL);
		len = result.length;
		Log.e(TAG, "PreSetCode: Bytes: " + Utils.dumpByteArray(DO_SL));
		Log.e(TAG, "DO SetCode: Result: " + len + "; Bytes: " + Utils.dumpByteArray(result));
		if (!(result[len-2]==(byte)0x90&&result[len-1]==(byte) 0x00))
			throw new IOException("could not retrieve result of operation");
    }
    
    /**
     * Request current code from card
     * @param isodep
     * @return
     * @throws IOException
     */
    public static byte doCodeReq(IsoDep isodep) throws IOException{
    	byte[] GET_MSISDN = {
    		(byte) 0x80, // CLA Class
    		(byte) 0x04, // INS Instruction
    		(byte) 0x00, // P1 Parameter 1
    		(byte) 0x00, // P2 Parameter 2
    		(byte) 0x10 // LE maximal number of bytes expected in result
    	};
    	
    	byte[] result = isodep.transceive(GET_MSISDN);
		int len = result.length;
		Log.e(TAG, "Result: " + len + "; Bytes: " + Utils.dumpByteArray(result));
		
		if (!(result[len-2]==(byte)0x90&&result[len-1]==(byte) 0x00))
			throw new IOException("could not retrieve msisdn");
		
		byte[] data = new byte[len-2];
		System.arraycopy(result, 0, data, 0, len-2);
		if (data!=null && data.length>0){
			byte code = data[0];
			Log.e(TAG, "Code: " + code);
			return code;
		}
		
		throw new IOException("Answer expected");
    }
    
    /**
     * Creates NDEF record containing string value
     * @param text
     * @return
     * @throws UnsupportedEncodingException
     */
    public static NdefRecord createRecord(String text) throws UnsupportedEncodingException {
        String lang       = "en";
        byte[] textBytes  = text.getBytes();
        byte[] langBytes  = lang.getBytes("US-ASCII");
        int    langLength = langBytes.length;
        int    textLength = textBytes.length;
        byte[] payload    = new byte[1 + langLength + textLength];

        // set status byte (see NDEF spec for actual bits)
        payload[0] = (byte) langLength;

        // copy langbytes and textbytes into payload
        System.arraycopy(langBytes, 0, payload, 1,              langLength);
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, 
                                           NdefRecord.RTD_TEXT, 
                                           new byte[0], 
                                           payload);

        return record;
    }
    
    /**
     * Creates NDEF message containing one MIME NDEF record with string given
     * @param msg
     * @return
     * @throws UnsupportedEncodingException 
     */
    public static NdefMessage createNdefMsg(String msg) throws UnsupportedEncodingException {
    	NdefRecord[] records = { createRecord(msg) };
        NdefMessage  message = new NdefMessage(records);
        return message;
        /*byte[] textBytes = msg.getBytes();
        NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
            "application/vnd.facebook.places".getBytes(), new byte[] {}, textBytes);
        return new NdefMessage(new NdefRecord[] { textRecord });*/
    	
    	
    }
}