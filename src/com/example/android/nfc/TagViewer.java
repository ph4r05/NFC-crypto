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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.nfc.record.ParsedNdefRecord;
import com.example.android.nfc.simulator.FakeTagsActivity;
import com.google.common.base.Charsets;
import com.google.common.primitives.Bytes;

/**
 * An {@link Activity} which handles a broadcast of a new tag that the device
 * just discovered.
 */
public class TagViewer extends Activity {

    static final String TAG = "ViewTag";
    
    static byte newCode = 0;
    static boolean setNewCode=false;
    
    static boolean setNewMessage=false;
    static String newMessage="";

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
    
    int number = 0;
    int iter = 0;
    TextView mTitle;
    TextView mCode;
    LinearLayout mTagContent;
    Tag curTag;

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
    
    public void doSetCode(IsoDep isodep, byte code) throws IOException{
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
		Log.e(TAG, "PreSetCode: Bytes: " + this.dumpByteArray(DO_SL));
		Log.e(TAG, "DO SetCode: Result: " + len + "; Bytes: " + this.dumpByteArray(result));
		if (!(result[len-2]==(byte)0x90&&result[len-1]==(byte) 0x00))
			throw new IOException("could not retrieve result of operation");
    }
    
    public byte doCodeReq(IsoDep isodep) throws IOException{
    	byte[] GET_MSISDN = {
    		(byte) 0x80, // CLA Class
    		(byte) 0x04, // INS Instruction
    		(byte) 0x00, // P1 Parameter 1
    		(byte) 0x00, // P2 Parameter 2
    		(byte) 0x10 // LE maximal number of bytes expected in result
    	};
    	
    	byte[] result = isodep.transceive(GET_MSISDN);
		int len = result.length;
		Log.e(TAG, "Result: " + len + "; Bytes: " + this.dumpByteArray(result));
		
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
    
    private NdefRecord createRecord(String text) throws UnsupportedEncodingException {
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
    
    public static NdefMessage createNdefMsg(String msg) {
        byte[] textBytes = msg.getBytes();
        NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
            "application/vnd.facebook.places".getBytes(), new byte[] {}, textBytes);
        return new NdefMessage(new NdefRecord[] { textRecord });
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
            	this.curTag = tag;
            	
            	if (tag==null){
            		Log.e(TAG, "Tag is null!");
            	}
            	
            	String[] techs = tag.getTechList();
            	for(int i=0; i<techs.length; i++){
            		Log.e(TAG, "Tech: " + techs[i]);
            	}
            	
            	// write message
            	if (TagViewer.setNewMessage){
            		TagViewer.setNewMessage=false;
            		boolean resNFCMsg = this.setNFCMessage("Last: " + TagViewer.newMessage);
            		if (resNFCMsg){
        				Toast toast = Toast.makeText(this.getApplicationContext(), "NFC msg was set to: " + TagViewer.newMessage, Toast.LENGTH_SHORT);
        				toast.show();
            		} else { 
                		Log.e(TAG, "Cannot set code");
                		Toast toast = Toast.makeText(this.getApplicationContext(), "Problem with setting NFC message!", Toast.LENGTH_SHORT);
                		toast.show();
            		}
            	}
            	
            	IsoDep isodep = IsoDep.get(tag);
            	if (isodep!=null){
            		isodep.connect();
            		byte[] result = null;
            		int len = 0;
            		
            		// select
                	this.doSelect(isodep);
                	
                	// do operation on card
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
                	
                	// set new code
                	if (TagViewer.setNewCode){
                		try {
                			this.doSetCode(isodep, TagViewer.newCode);
                			TagViewer.setNewCode = false;
            				Toast toast = Toast.makeText(this.getApplicationContext(), "Code was set to: " + TagViewer.newCode, Toast.LENGTH_SHORT);
            				toast.show();
            			}
                		catch(Exception e){
                    		Log.e(TAG, "Cannot set code", e);
                    		Toast toast = Toast.makeText(this.getApplicationContext(), "Problem with setting code!", Toast.LENGTH_SHORT);
                    		toast.show();
                		}
                	}
                	
                	// get identification with APDU
                	byte code = this.doCodeReq(isodep);
        			Log.e(TAG, "Code: " + code);
                    
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
                    		+ n1 + " op " + n2 + " = " + res);
                    mCode.setTextSize(40);
                    mCode.setBackgroundColor(code==1 ? Color.BLUE : Color.GREEN);
                    
                    
                    FakeTagsActivity.number = this.number;
                    FakeTagsActivity.iter = this.iter;
                    if (parent!=null){
                    	//parent.setNumber(this.number);
                    	//parent.setIter(this.iter);
                    }
            		isodep.close();
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
      savedInstanceState.putInt("number", this.number);
      savedInstanceState.putInt("iter", this.iter);
    }
    
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
      super.onRestoreInstanceState(savedInstanceState);
      // Restore UI state from the savedInstanceState.
      // This bundle has also been passed to onCreate.
      if (savedInstanceState!=null){
	      if (savedInstanceState.containsKey("number"))
	      	this.number = savedInstanceState.getInt("number");
	      if (savedInstanceState.containsKey("iter"))
	      	this.iter = savedInstanceState.getInt("iter");
      }
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
	protected Dialog onCreateDialog(int id) {
		return super.onCreateDialog(id);
	}
    
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.tagmenu, menu);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
		// respond to menu item selection
		switch (item.getItemId()) {
		case R.id.setMsg:
			this.showAlert();
			return true;
		case R.id.delMsg:
			Log.e(TAG, "Delete message selected");
			return true;
		case R.id.setCode:
			Log.e(TAG, "Setting code");
			this.showSetCodeAlert();
		default:
			return super.onOptionsItemSelected(item);
		}
    }
    
    public void showSetCodeAlert(){
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Code change");
		alert.setMessage("Enter code:");
		
		// Set an EditText view to get user input
		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_NUMBER);
		alert.setView(input);
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = input.getText().toString();
				Log.e(TAG, "New code: " + value);
				setCode(value);
				return;
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						return;
					}
				});
		
		alert.show();
    }
    
	public void showAlert() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Enter message");
		alert.setMessage("Enter message:");

		// Set an EditText view to get user input
		final EditText input = new EditText(this);
		alert.setView(input);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = input.getText().toString();
				Log.e(TAG, "Pin Value : " + value);
				setSetNewMessage(true);
				setNewMessage(value);
				setNFCMessage(value);
				return;
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						return;
					}
				});
		
		alert.show();
		
	}

	public void setCode(String code){
		try {
			byte myCode = Byte.parseByte(code);
			TagViewer.setNewCode=true;
			TagViewer.newCode = myCode;
			
			Toast toast = Toast.makeText(this.getApplicationContext(), "Code will be set to: " + myCode, Toast.LENGTH_SHORT);
			toast.show();
    	} catch(Exception e){
    		Log.e(TAG, "Cannot set code", e);
    		Toast toast = Toast.makeText(this.getApplicationContext(), "Problem with setting code!", Toast.LENGTH_SHORT);
    		toast.show();
    	}
	}
	
	public boolean setNFCMessage(String message) {
		try {
			//NdefRecord[] records = { this.createRecord(message) };
		    //NdefMessage  ndefmsg = new NdefMessage(records);
			NdefMessage  ndefmsg = createNdefMsg(message);
			
			// see if tag is already NDEF formatted
			Ndef ndef = Ndef.get(this.curTag);
			if (ndef != null) {
				ndef.connect();
				if (!ndef.isWritable()) {
					Log.e(TAG, "Read-only tag.");
					return false;
				}
				
				// work out how much space we need for the data
				ndef.writeNdefMessage(ndefmsg);
				ndef.close();
				Log.e(TAG, "Tag written successfully.");
				return true;
			} else {
				// attempt to format tag
				NdefFormatable format = NdefFormatable.get(this.curTag);
				if (format != null) {
					try {
						format.connect();
						Log.e(TAG, "Connected: " + (format.isConnected() ? "YES":"NO"));
						
						//int serviceHandle = format.getServiceHandle();
						//INfcTag tagService = format.getTagService();
						//int errorCode = tagService.formatNdef(serviceHandle, MifareClassic.KEY_DEFAULT);
						
						format.format(ndefmsg);
						Log.e(TAG, "Tag written successfully!");
						return true;
					} catch (IOException e) {
						Log.e(TAG, "Unable to format tag to NDEF. Msg: " + e.getMessage(), e);
						return false;
					} finally {
						format.close();
					}
				} else {
					Log.e(TAG, "Tag doesn't appear to support NDEF format.");
					return false;
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to write tag", e);
		}
			
		return false;
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

	public static byte getNewCode() {
		return newCode;
	}

	public static void setNewCode(byte newCode) {
		TagViewer.newCode = newCode;
	}

	public static boolean isSetNewCode() {
		return setNewCode;
	}

	public static void setSetNewCode(boolean setNewCode) {
		TagViewer.setNewCode = setNewCode;
	}

	public static boolean isSetNewMessage() {
		return setNewMessage;
	}

	public static void setSetNewMessage(boolean setNewMessage) {
		TagViewer.setNewMessage = setNewMessage;
	}

	public static String getNewMessage() {
		return newMessage;
	}

	public static void setNewMessage(String newMessage) {
		TagViewer.newMessage = newMessage;
	}
}
