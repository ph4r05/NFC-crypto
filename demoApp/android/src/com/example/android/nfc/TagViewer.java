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
import java.util.List;
import java.util.Random;

import utils.CardOperations;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
    
    int number = 0;
    int iter = 0;
    TextView mTitle;
    TextView mCode;
    LinearLayout mTagContent;
    Tag curTag;

	private NfcAdapter mAdapter;
	private PendingIntent mPendingIntent;
	private IntentFilter[] mFilters;
	private String[][] mTechLists;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.e(TAG, "TagViewer Activity created");
        setContentView(R.layout.tag_viewer);
        mTagContent = (LinearLayout) findViewById(R.id.list);
        mTitle = (TextView) findViewById(R.id.title);
        mCode = (TextView) findViewById(R.id.code);
        
        // recover state from bundle
        if (savedInstanceState!=null){
        	Log.e(TAG, "Have some saved instance!");
	        if (savedInstanceState.containsKey("number"))
	        	this.number = savedInstanceState.getInt("number");
	        if (savedInstanceState.containsKey("iter"))
	        	this.iter = savedInstanceState.getInt("iter");
        }
        
        // steal NFC intents 
        mAdapter = NfcAdapter.getDefaultAdapter(this);
        // declare 
        mPendingIntent = PendingIntent.getActivity(
        		this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        mFilters = new IntentFilter[] { ndef, };
        mTechLists = new String[][] { new String[] { IsoDep.class.getName(), Ndef.class.getName(), NdefFormatable.class.getName() } };
        
        // resolve
        resolveIntent(getIntent());
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
                    
                    try {
                    	Log.d(TAG, "MSG: " + msgs[i]);
                    } catch(Exception e){
                    	
                    }
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
            	
            	StringBuilder sbDump = new StringBuilder();
            	
            	String[] techs = tag.getTechList();
            	for(int i=0; i<techs.length; i++){
            		Log.e(TAG, "Tech: " + techs[i]);
            		sbDump.append("Tech: ").append(techs[i]).append("\n");
            	}
            	
            	mCode.setText(sbDump.toString());
                mCode.setBackgroundColor(Color.BLUE);
            	
            	// write message
            	if (TagViewer.setNewMessage){
            		TagViewer.setNewMessage=false;
            		boolean resNFCMsg = this.setNFCMessage(TagViewer.newMessage);
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
                	CardOperations.doSelect(isodep);
                	
                	// do operation on card
                	Random r  = new Random();
                	short n1  = (short) (r.nextInt(20)-10);
                	short n2  = (short) (r.nextInt(20)-10);
                	short res = 0;
                	try {
                		res = CardOperations.doOperation(isodep, n1, n2);
                		Log.e(TAG, "Result of op. "+n1+" . "+n2+" = "+res);
                	} catch (Exception exc){
                		Log.e(TAG, "Cannot perform operation", exc);
                	}
                	
                	// set new code
                	if (TagViewer.setNewCode){
                		try {
                			CardOperations.doSetCode(isodep, TagViewer.newCode);
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
                	byte code = CardOperations.doCodeReq(isodep);
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
                    
                    mCode.setText(sbDump.toString() + "Code: " + code + "\nNum: " + this.number + " ; Iter: " + this.iter + "\n"
                    		+ n1 + " op " + n2 + " = " + res);
                    //mCode.setTextSize(40);
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
      savedInstanceState.putInt("number", this.number);
      savedInstanceState.putInt("iter", this.iter);
    }
    
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
      super.onRestoreInstanceState(savedInstanceState);
      if (savedInstanceState!=null){
	      if (savedInstanceState.containsKey("number"))
	      	this.number = savedInstanceState.getInt("number");
	      if (savedInstanceState.containsKey("iter"))
	      	this.iter = savedInstanceState.getInt("iter");
      }
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
    
	@Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }
    
    @Override
	protected void onPause() {
		super.onPause();
		try {
			mAdapter.disableForegroundDispatch(this);
		} catch (Exception ex){
			Log.e(TAG, "Problem with mAdapter", ex);
		}
		
		Log.i(TAG, "TagViewer paused");
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			mAdapter.enableForegroundDispatch(this, this.mPendingIntent, this.mFilters, this.mTechLists);
		} catch (Exception ex){
			Log.e(TAG, "Problem with mAdapter", ex);
		}
		
		Log.i(TAG, "TagViewer resumed");
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
			NdefMessage  ndefmsg = CardOperations.createNdefMsg(message);
			
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
