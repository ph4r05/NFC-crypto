package com.example.android.nfc;

import java.util.Set;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.nfc.simulator.FakeTagsActivity;

/**
 * A activity that launches tags as if they had been scanned.
 */
public class NFCTags extends Activity {

    static final String TAG = "MainApp-NFCTags";
    static final byte[] UID = new byte[] {0x05, 0x00, 0x03, 0x08};
    private NfcAdapter mAdapter;
	private PendingIntent mPendingIntent;
	private IntentFilter[] mFilters;
	private String[][] mTechLists;
    
    public static int iter;
    public static int number;
    
    private static int routeToClass=1;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        Log.e(TAG, "TagViewer Activity created");
        
        // set content view
        setContentView(R.layout.tag_nfcmain);
        
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

    /**
     * Take basic routing care
     * @param intent
     */
    void resolveIntent(Intent intent) 
    {
        // Parse the intent
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) 
        {
        	final Context context = this.getApplicationContext();			
        	final Intent intent2 = new Intent(NfcAdapter.ACTION_TAG_DISCOVERED);
            intent2.putExtras(intent);
            intent2.setClass(context, routeToClass==1 ? TagViewerPrivileges.class : TagViewer.class);
            intent2.setFlags(intent.getFlags());
            Set<String> categories = intent.getCategories();
            if (categories!=null){
	            for (String category : categories){
	            	intent2.addCategory(category);
	            }
            }
            
			//intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //intent2.addCategory(Intent.CATEGORY_LAUNCHER);
			
            Log.i(TAG, "Routing intent to different class");
            startActivity(intent2);
        }
    }

    public void onNewIntent(Intent intent) {    	
    	setIntent(intent);
        resolveIntent(intent);
        Log.i("Foreground dispatch", "Discovered tag with intent: " + intent);
    }
    
	@Override
	protected void onPause() {
		super.onPause();
		try {
			mAdapter.disableForegroundDispatch(this);
		} catch (Exception ex){
			Log.e(TAG, "Problem with mAdapter", ex);
		}
		
		Log.i(TAG, "onPause");
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		Log.i(TAG, "onRestart");
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			mAdapter.enableForegroundDispatch(this, this.mPendingIntent, this.mFilters, this.mTechLists);
		} catch (Exception ex){
			Log.e(TAG, "Problem with mAdapter", ex);
		}
		
		Log.i(TAG, "onResume");
	}  
	
	@Override
	protected void onStop() {
		super.onStop();
		Log.i(TAG, "onStop");
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		return super.onCreateDialog(id);
	}
    
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.nfctags_menu, menu);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
		// respond to menu item selection
    	Context context;
    	Intent intent;
    	
		switch (item.getItemId()) {
		
			case R.id.simulator:
				Log.i(TAG, "Switching to simulator activity");
				context = this.getApplicationContext();
				intent = new Intent(Intent.ACTION_MAIN); 
				intent.setClass(context, FakeTagsActivity.class);
				intent.addCategory(Intent.CATEGORY_LAUNCHER);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			    context.startActivity(intent); 
				return true;
				
			case R.id.authenticator:
				Log.i(TAG, "Switching to authenticator activity");
				context = this.getApplicationContext();
				intent = new Intent(Intent.ACTION_MAIN); 
				intent.setClass(context, TagViewerPrivileges.class);
				intent.addCategory(Intent.CATEGORY_LAUNCHER);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			    context.startActivity(intent); 
				return true;
				
			case R.id.useTagView:
				if (item.isChecked()) item.setChecked(false);
	            else item.setChecked(true);
				
				routeToClass = item.isChecked() ? 2 : 1; 
				Log.i(TAG, "Use another activity to dispatch NFC: " + routeToClass);
				
				Toast toast = Toast.makeText(this.getApplicationContext(), ("NFC will be routed to: " + (routeToClass ==1 ? "Privileges":"Display")), Toast.LENGTH_SHORT);
				toast.show();
				return true;
				
			default:
				return super.onOptionsItemSelected(item);
		}
    }
	
    
}
