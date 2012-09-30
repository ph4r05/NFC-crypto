/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.nfc;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.android.nfc.record.ParsedNdefRecord;
import com.example.android.nfc.simulator.FakeTagsActivity;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * An {@link Activity} which handles a broadcast of a new tag that the device
 * just discovered.
 */
public class TagViewer extends Activity {

    static final String TAG = "ViewTag";

    /**
     * This activity will finish itself in this amount of time if the user
     * doesn't do anything.
     */
    static final int ACTIVITY_TIMEOUT_MS = 1 * 1000;

    public static byte[] SELECT = { (byte) 0x00, // CLA Class 
    		(byte) 0xA4, // INS Instruction 
    		(byte) 0x04, // P1 Parameter 1 
    		(byte) 0x00, // P2 Parameter 2 
    		(byte) 0x09, // Length
    		(byte) 0xa0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x0c, 0x01 
    		};
    
    public static byte[] GET_MSISDN = {
    		(byte) 0x80, // CLA Class
    		(byte) 0x04, // INS Instruction
    		(byte) 0x00, // P1 Parameter 1
    		(byte) 0x00, // P2 Parameter 2
    		(byte) 0x10 // LE maximal number of bytes expected in result
    };
    
    int number = 0;
    int iter = 0;
    TextView mTitle;
    TextView mCode;

    LinearLayout mTagContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.e(TAG, "TagViewer Activity created");

        setContentView(R.layout.tag_viewer);
        mTagContent = (LinearLayout) findViewById(R.id.list);
        mTitle = (TextView) findViewById(R.id.title);
        
        if (savedInstanceState!=null){
        	Log.e(TAG, "Have some saved instance!");
	        if (savedInstanceState.containsKey("number"))
	        	this.number = savedInstanceState.getInt("number");
	        if (savedInstanceState.containsKey("iter"))
	        	this.iter = savedInstanceState.getInt("iter");
        }
        
        resolveIntent(getIntent());
    }
    
    public static short readShort(byte[] data, short offset) {
    	return (short) (((data[offset] << 8)) | ((data[offset + 1] & 0xff)));
    }

    public static byte[] shortToByteArray(short s) {
    	return new byte[] { (byte) ((s & 0xFF00) >> 8), (byte) (s & 0x00FF) };
    }
    
    public static void shortToExistingByteArray(short s, byte[] buff, short offset) {
    	buff[offset]   = (byte)((s & 0xFF00) >> 8);
    	buff[offset+1] =  (byte) (s & 0x00FF);
    }
    
    public boolean doSelect(IsoDep isodep) throws IOException{
    	// select 
    	byte[] result;
    	int len;
    	
    	result = isodep.transceive(SELECT);
		len = result.length;
		Log.e(TAG, "Select, Result: " + len + "; Bytes: " + this.dumpByteArray(result));
		if (!(result[len-2]==(byte)0x90&&result[len-1]==(byte) 0x00))
			throw new IOException("could not select");
		return true;
    }
    
    public short doOperation(IsoDep isodep, short num1, short num2) throws IOException{
    	byte[] DO_OP = {
    		(byte) 0x80, // CLA Class
    		(byte) 0x02, // INS Instruction
    		(byte) 0x00, // P1 Parameter 1
    		(byte) 0x00, // P2 Parameter 2
    		(byte) 0x04, // 2 short numbers
    		(byte) 0x00, 0x00, 0x00, 0x00,
    		(byte) 0x10 // LE maximal number of bytes expected in result
    	};
    	
    	shortToExistingByteArray(num1, DO_OP, (short)5);
    	shortToExistingByteArray(num2, DO_OP, (short)7);
    	
    	// select 
    	byte[] result;
    	int len;
		
		result = isodep.transceive(DO_OP);
		len = result.length;
		Log.e(TAG, "PreOP Op: Bytes: " + this.dumpByteArray(DO_OP));
		Log.e(TAG, "DO Op: Result: " + len + "; Bytes: " + this.dumpByteArray(result));
		if (!(result[len-2]==(byte)0x90&&result[len-1]==(byte) 0x00))
			throw new IOException("could not retrieve result of operation");
		
		// convert result to short
		short resultShort = readShort(result, (short)0);
    	return resultShort;
    }

    void resolveIntent(Intent intent) {
    	
        // Parse the intent
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
        	Log.e(TAG, "Tag discovered right now!");
        	
            // When a tag is discovered we send it to the service to be save. We
            // include a PendingIntent for the service to call back onto. This
            // will cause this activity to be restarted with onNewIntent(). At
            // that time we read it from the database and view it.
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs;
            if (rawMsgs != null) {
            	Log.e(TAG, "We have messages"); 
            	
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            } else {
                // Unknown tag type
            	Log.e(TAG, "No message here!");
            	
                byte[] empty = new byte[] {};
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty);
                NdefMessage msg = new NdefMessage(new NdefRecord[] {record});
                msgs = new NdefMessage[] {msg};
            }
             
            try{
            	Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            	if (tag==null){
            		Log.e(TAG, "Tag is null!");
            	}
            	
            	String[] techs = tag.getTechList();
            	for(int i=0; i<techs.length; i++){
            		Log.e(TAG, "Tech: " + techs[i]);
            	}
            	
            	IsoDep isodep = IsoDep.get(tag);
            	if (isodep!=null){
            		isodep.connect();
            		byte[] result = null;
            		int len = 0;
            		
            		// select
                	this.doSelect(isodep);
                	
                	// do operation:
                	Random r  = new Random();
                	short n1  = (short) (r.nextInt(20)-10);
                	short n2  = (short) (r.nextInt(20)-10);
                	short res = 0;
                	try {
                		res = this.doOperation(isodep, n1, n2);
                		Log.e(TAG, "Result of op. "+n1+" . "+n2+" = "+res);
                	} catch (Exception exc){
                		Log.e(TAG, "Cannot perform operation", exc);
                	}
                	
                	// get identification with APDU
            		result = isodep.transceive(GET_MSISDN);
            		len = result.length;
            		Log.e(TAG, "Result: " + len + "; Bytes: " + this.dumpByteArray(result));
            		
            		if (!(result[len-2]==(byte)0x90&&result[len-1]==(byte) 0x00))
            			throw new IOException("could not retrieve msisdn");
            		
            		byte[] data = new byte[len-2];
            		System.arraycopy(result, 0, data, 0, len-2);
            		if (data!=null && data.length>0){
            			byte code = data[0];
            			Log.e(TAG, "Code: " + code);
            			
            			byte[] empty = new byte[] {};
                        NdefRecord record = new NdefRecord(NdefRecord.TNF_ABSOLUTE_URI, empty, empty, data);
                        NdefMessage msg = new NdefMessage(new NdefRecord[] {record});
                        msgs = new NdefMessage[] {msg};
                        
                        //FakeTagsActivity.newTextRecord("Code: " + code, , encodeInUtf8)
                        this.number = FakeTagsActivity.number;
                        this.iter = FakeTagsActivity.iter;
                        
                        Application app = this.getApplication();
                        FakeTagsActivity parent = (FakeTagsActivity) this.getParent();
                        if (parent!=null){
                        	//parent.getNumber();
                        	//this.iter = parent.getIter();
                        }
                        
                        this.number+= code==1 ? 1:-1;
                        this.iter+=1;
                        mCode = (TextView) findViewById(R.id.code);
                        mCode.setText("Code: " + code + "\nNum: " + this.number + " ; Iter: " + this.iter + "\n"
                        		+ n1 + "." + n2 + "=" + res);
                        mCode.setTextSize(40);
                        mCode.setBackgroundColor(code==1 ? Color.BLUE : Color.GREEN);
                              
                        FakeTagsActivity.number = this.number;
                        FakeTagsActivity.iter = this.iter;
                        if (parent!=null){
                        	//parent.setNumber(this.number);
                        	//parent.setIter(this.iter);
                        }
            		}
            		isodep.close();
            	}
            	
                Ndef ndef = Ndef.get(tag);
	            if (ndef!=null){
	            	Log.e(TAG, "ndef is not null");
	            	ndef.connect();
	            	if (ndef.isWritable()){
	            		Log.e(TAG, "ndef is writable");
	            		
	            	}
	            } else {
	            	Log.e(TAG, "ndef is null");
	            }
            } catch(Exception ex){
            	Log.e(TAG, "exception caught: " + ex.getMessage());
            }
            
            // Setup the views
            setTitle(R.string.title_scanned_tag);
            buildTagViews(msgs);
        } else {
            Log.e(TAG, "Unknown intent " + intent);
            finish();
            return;
        }
    }
    
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
      super.onSaveInstanceState(savedInstanceState);
      // Save UI state changes to the savedInstanceState.
      // This bundle will be passed to onCreate if the process is
      // killed and restarted.
      //savedInstanceState.putBoolean("MyBoolean", true);
      //savedInstanceState.putDouble("myDouble", 1.9);
      savedInstanceState.putInt("number", this.number);
      savedInstanceState.putInt("iter", this.iter);
      //savedInstanceState.putString("MyString", "Welcome back to Android");
      // etc.
    }
    
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
      super.onRestoreInstanceState(savedInstanceState);
      // Restore UI state from the savedInstanceState.
      // This bundle has also been passed to onCreate.
      //boolean myBoolean = savedInstanceState.getBoolean("MyBoolean");
      //double myDouble = savedInstanceState.getDouble("myDouble");
      //int myInt = savedInstanceState.getInt("MyInt");
      if (savedInstanceState!=null){
	      if (savedInstanceState.containsKey("number"))
	      	this.number = savedInstanceState.getInt("number");
	      if (savedInstanceState.containsKey("iter"))
	      	this.iter = savedInstanceState.getInt("iter");
      }
      //String myString = savedInstanceState.getString("MyString");
    }
    
    public String dumpByteArray(byte[] b){
        return this.dumpByteArray(b, 0, b.length);
    }
    
    public String dumpByteArray(byte[] b, int offset, int size){
    	StringBuilder sb = new StringBuilder();
        for(int i=offset; i<(offset+size); i++){
            if (((i-offset) % 16) == 0) sb.append("| ");
            sb.append(String.format("%02X", b[i]) + " ");
        } 
        sb.append("\n");
        return sb.toString();
    }

    void buildTagViews(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout content = mTagContent;
        // Clear out any old views in the content area, for example if you scan
        // two tags in a row.
        content.removeAllViews();
        // Parse the first message in the list
        // Build views for all of the sub records
        List<ParsedNdefRecord> records = NdefMessageParser.parse(msgs[0]);
        final int size = records.size();
        for (int i = 0; i < size; i++) {
            ParsedNdefRecord record = records.get(i);
            content.addView(record.getView(this, inflater, content, i));
            inflater.inflate(R.layout.tag_divider, content, true);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle.setText(title);
    }
}
