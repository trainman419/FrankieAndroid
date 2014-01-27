package com.namniart.frankie;

import java.util.ArrayList;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

public class MainActivity extends Activity {
	// instance variables
	RobotApplication mApp;
    private Joystick joystickHandler_;

    // bluetooth-related variables
	private BluetoothDevice mDevice;
	private ArrayList<BluetoothDevice> mDevices;
	// activity codes
	public static final int CHOOSE_DEVICE=1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
        mApp = (RobotApplication)this.getApplication();
        joystickHandler_ = new Joystick(mApp);
	}

	private static final int CHOOSE_ID = Menu.FIRST;
	private static final int STOP_ID = Menu.FIRST + 1;
	/**
	 * create the context menu for this Activity
	 */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
    	menu.add(0, CHOOSE_ID, 0, R.string.bluetooth_picker);
    	menu.add(0, STOP_ID, 0, R.string.bluetooth_stop);
    	return true;
    }

    /**
     * callback for selection of a menu item
     */
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	Intent i;
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	
    	builder.setMessage("Generic Message").setCancelable(false);
    	builder.setNeutralButton("Ok", null);
    	       
        switch (item.getItemId()) {
        case CHOOSE_ID:
        	mApp.stopHwMan();
        	Dialog dialog = ProgressDialog.show(this, "", "Looking for Bluetooth devices...", true);
        	BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        	
	        if( bt != null ) {
		        Set<BluetoothDevice> devices = bt.getBondedDevices();
		        // display list of adapters; allow the user to pick one
		        if( devices != null && devices.isEmpty() ) {
		        	dialog.dismiss();
		        	dialog = builder.setMessage("No devices found; please pair a device with your phone.").create();
		        	dialog.show();
		        } else {		        	
		        	ArrayList<BluetoothDevice> list = new ArrayList<BluetoothDevice>();
		        	for( BluetoothDevice dev : devices ) {
		        		list.add(dev);
		        	}
		        	
		        	i = new Intent(this, BluetoothChooser.class);
		        	i.putExtra("devices", list);
		        	mDevices = list;
		        	dialog.dismiss();
		        	try {
		        		startActivityForResult(i, CHOOSE_DEVICE);
		        	} catch(Exception e) {
			        	dialog = builder.setMessage("Failed to start bluetooth chooser: " + e.getMessage()).create();
			        	dialog.show();
		        	}
		        }
	        } else {
	        	dialog.dismiss();
	        	dialog = builder.setMessage("No Bluetooth found").create();
	        	dialog.show();
	        }
        	return true;
        case STOP_ID:
        	// stop the HardwareManager
        	mApp.stopHwMan();
        	return true;        
        }
        return super.onMenuItemSelected(featureId, item);
    }

	/* this is called before onResume(), so we let onResume do the work of starting the worker thread
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if(resultCode == RESULT_OK) {
	        switch(requestCode) {
	        case CHOOSE_DEVICE:
	        	if( mDevices != null) {
	        		mDevice = mDevices.get(intent.getIntExtra("index",0));
	        		mDevices = null; // free up our device list
	    			mApp.startHwMan(mDevice);
	        	}
	        	break;
	        }
        }
    }
	
    /**
    * Until we get a joystick event, it is not certain what InputDevice corresponds to the joystick
    * Though there are methods for introspection, I was in a hurry and this does the job.
    */
    private void initializeJoystickHandlerIfPossibleAndNecessary(InputEvent event)
    /*************************************************************************/
    {
        if ((!joystickHandler_.isInitialized()) && (event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0)
            joystickHandler_.initializeDevice(event.getDevice());
    }
    
    /**
    * This is an android event handler for generic motion events. This is triggered
    * when any of the axes controls (vs simple buttons) on the joystick are used
    * where each axis has a value between -1.0 and 1.0.
    */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event)
    /*************************************************************************/
    {
        Log.d("MainActivity", "MainActivity: dispatchGenericMotionEvent");

        initializeJoystickHandlerIfPossibleAndNecessary(event);

        boolean isJoystickEvent = ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0);
        boolean isActionMoveEvent = event.getAction() == MotionEvent.ACTION_MOVE;

        if (!isJoystickEvent || !isActionMoveEvent || !joystickHandler_.isInitialized())
            return super.dispatchGenericMotionEvent(event);
        
        if (joystickHandler_.onJoystickMotion(event))
            return true;

        return super.dispatchGenericMotionEvent(event);
    }
}
