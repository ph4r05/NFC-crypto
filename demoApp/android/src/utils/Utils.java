package utils;

public class Utils {
	/**
	 * Return short from byte representation
	 * @param data
	 * @param offset
	 * @return
	 */
    public static short readShort(byte[] data, short offset) {
    	return (short) (((data[offset] << 8)) | ((data[offset + 1] & 0xff)));
    }

    /**
     * Returns new byte array containing byte representation of short
     * @param s
     * @return
     */
    public static byte[] shortToByteArray(short s) {
    	return new byte[] { (byte) ((s & 0xFF00) >> 8), (byte) (s & 0x00FF) };
    }
    
    /**
     * Converts short data type to byte representation to specified byte array from given offset
     * @param s
     * @param buff
     * @param offset
     */
    public static void shortToExistingByteArray(short s, byte[] buff, short offset) {
    	buff[offset]   = (byte)((s & 0xFF00) >> 8);
    	buff[offset+1] =  (byte) (s & 0x00FF);
    }
    
    /**
     * Dumps byte array to hexadecimal string
     * @param b
     * @param offset
     * @param size
     * @return
     */
    public static String dumpByteArray(byte[] b){
        return dumpByteArray(b, 0, b.length);
    }
    
    /**
     * Dumps byte array to hexadecimal string
     * @param b
     * @param offset
     * @param size
     * @return
     */
    public static String dumpByteArray(byte[] b, int offset, int size){
    	StringBuilder sb = new StringBuilder();
        for(int i=offset; i<(offset+size); i++){
            if (((i-offset) % 16) == 0) sb.append("| ");
            sb.append(String.format("%02X", b[i]) + " ");
        } 
        sb.append("\n");
        return sb.toString();
    }
}