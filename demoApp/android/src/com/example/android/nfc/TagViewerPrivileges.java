package com.example.android.nfc;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.nfc.simulator.FakeTagsActivity;

public class TagViewerPrivileges extends Activity {
    static final String TAG = "ViewTagPrivs";

    private int state=0;

    /**
     * This activity will finish itself in this amount of time if the user
     * doesn't do anything.
     */
    static final int ACTIVITY_TIMEOUT_MS = 1 * 1000;
    
    int number = 0;
    int iter = 0;
    private TextView mTitle;
    private TextView mResult;
    private TextView mTimerOut;
    private TextView mDetails;
    private int timerVal=0;
    private Set<Integer> cardCodes = new HashSet<Integer>();
    
    LinearLayout mTagContent;
    Tag curTag;

	private NfcAdapter mAdapter;
	private PendingIntent mPendingIntent;
	private String[][] mTechLists;
	private IntentFilter[] mFilters;
	
	Timer timer = new Timer();
    
	//tells activity to run on ui thread
   class myTimerTask extends TimerTask {
        @Override
        public void run() {
            TagViewerPrivileges.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                	TagViewerPrivileges.this.timerTick();
                }
            });
        }
   };
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.e(TAG, "TagViewerPrivileges Activity created");
        setContentView(R.layout.tag_viewer_priv);
        mTagContent = (LinearLayout) findViewById(R.id.list);
        mTitle = (TextView) findViewById(R.id.title);
        mResult = (TextView) findViewById(R.id.result);
        mTimerOut = (TextView) findViewById(R.id.timerOut);
        mDetails = (TextView) findViewById(R.id.tagdetails);
        mDetails.setText("");
        
        // recover state from bundle
        if (savedInstanceState!=null){
        	Log.e(TAG, "Have some saved instance!");
	        if (savedInstanceState.containsKey("number"))
	        	this.number = savedInstanceState.getInt("number");
	        if (savedInstanceState.containsKey("iter"))
	        	this.iter = savedInstanceState.getInt("iter");
        }
        
        // by default reflect state zero
        state=0;
        stateChanged(0);
        
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
        	
            try{
            	Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            	this.curTag = tag;
            	if (tag==null){
            		Log.e(TAG, "Tag is null!");
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
                	
                	// get identification with APDU
                	byte code = CardOperations.doCodeReq(isodep);
                	Integer codeInt = new Integer(code);
        			Log.e(TAG, "Code: " + code);
        			
        			// add new card to set
        			int prevCardCount = this.cardCodes.size();
        			this.cardCodes.add(codeInt);
                    
        			// all scanned to string
        			int fCodes=0;
        			StringBuilder sb = new StringBuilder();
        			for(Integer fCode : cardCodes){
        				if (fCodes>0) sb.append(", ");
        				sb.append(fCode);
        				fCodes++;
        			}
        			
                    this.number+= code==1 ? 1:-1;
                    this.iter+=1;
                    mDetails = (TextView) findViewById(R.id.tagdetails);
                    mDetails.setText("Code: " + code + "\nIter: " + this.iter + "\n" + n1 + " op " + n2 + " = " + res + "\nScanned codes: " + sb.toString());
                    //mDetails.setTextSize(40);
                    //mDetails.setBackgroundColor(code==1 ? Color.BLUE : Color.GREEN);
                    mDetails.setBackgroundColor(Color.BLUE);
            		isodep.close();
            		
            		// trigger state change?
            		if (prevCardCount==0 && this.cardCodes.size()==1){
            			this.stateChanged(1);
            		}
            		
            		if (prevCardCount==1 && this.cardCodes.size()==2){
            			this.stateChanged(2);
            		}
            	}
            } catch(Exception ex){
            	Log.e(TAG, "exception caught: " + ex.getMessage(), ex);
            }
        } else {
            Log.i(TAG, "Another intent arrived " + intent);
            //finish();
            return;
        }
    }
    
    public synchronized void timerTick(){
    	Log.i(TAG, "Timer ticked, val: " + this.timerVal);
    	
    	this.timerVal--;
    	this.mTimerOut = (TextView) findViewById(R.id.timerOut);
    	this.mTimerOut.setText(String.format("Timer: %02d", this.timerVal));
    	
    	if (this.timerVal<=0){
    		// stop timer at first
    		try {
				timer.cancel();
				timer.purge();
			} catch(Exception e){
				Log.e(TAG, "Problem stopping timer", e);
			}
    		
    		// do state change
    		if (this.state==2)
    			this.stateChanged(0);
    		else
    			this.stateChanged(this.state-1);
    	}
    }
    
    /**
     * React on state changed
     */
    public synchronized void stateChanged(int newState){
    	if (newState < 0) newState=0;
    	mResult = (TextView) findViewById(R.id.result);
    	mResult.setTextSize(40);
    	
    	try {
			timer.cancel();
			timer.purge();
		} catch(Exception ex){
			Log.e(TAG, "Problem with timer stop", ex);
		}
    	
    	switch (newState){
    	default:
    	case 0:
    		mResult.setBackgroundColor(Color.RED);
    		mResult.setText("Not authenticated");
    		mDetails.setText("");
            cardCodes.clear();
    		break;
    	
    	case 1:
    		mResult.setBackgroundColor(Color.YELLOW);
    		mResult.setText("Auth level 1");
    		
    		// start test timer
    		this.timer = new Timer();
    		this.timerVal = 10;//2*60;
            this.timer.schedule(new myTimerTask(), 0, 1000);
    		break;
    		
    	case 2:
    		mResult.setBackgroundColor(Color.GREEN);
    		mResult.setText("Auth level 2");
    		
    		// start test timer
    		this.timer = new Timer();
    		this.timerVal = 20;//5*60;
            this.timer.schedule(new myTimerTask(), 0, 1000);
    		break;
    	}
    	
    	Toast toast = Toast.makeText(this.getApplicationContext(), "State changed from: " + this.state + " to " + newState, Toast.LENGTH_SHORT);
		toast.show();
    	this.state = newState;
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
    
    @Override
	protected Dialog onCreateDialog(int id) {
		return super.onCreateDialog(id);
	}
    
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.tagviewpriv_menu, menu);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
		// respond to menu item selection
		switch (item.getItemId()) {
		case R.id.resetState:
			Log.i(TAG, "Reset state");
			this.state=0;
			
			try {
				timer.cancel();
				timer.purge();
			} catch(Exception e){
				Log.e(TAG, "Problem stopping timer", e);
			}
			this.timerVal=0;
			this.cardCodes.clear();
			this.stateChanged(0);
			
			Toast toast = Toast.makeText(this.getApplicationContext(), "State reseted", Toast.LENGTH_SHORT);
    		toast.show();
			return true;
			
		case R.id.waitMore:
			this.timerVal += 10;
			Log.i(TAG, "Wait more");
			return true;
			
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
		
		Log.i(TAG, "TagViewerPriv paused");
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			mAdapter.enableForegroundDispatch(this, this.mPendingIntent, this.mFilters, this.mTechLists);
		} catch (Exception ex){
			Log.e(TAG, "Problem with mAdapter", ex);
		}
		
		Log.i(TAG, "TagViewerPriv resumed");
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

    @Override
    public void setTitle(CharSequence title) {
        mTitle.setText(title);
    }
}