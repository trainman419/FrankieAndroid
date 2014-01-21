package com.namniart.frankie;

import android.util.Log;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.InputDevice.MotionRange;
import android.view.MotionEvent;

public class Joystick {
    private InputDevice device_;
    private boolean isInitialized_;
    private int messageSequenceNumber_;
    private int[] axes_;
    private float[] axesValues_;
    private SparseIntArray keys_;
    private int x_axis, y_axis;
    
   /**
    * The constructor for this class
    */
    public Joystick()
    {
        isInitialized_ = false;
        messageSequenceNumber_ = 0;
        
        // Right stick: y=5, x=4
        // Left  stick: y=1, x=0
        // Left trigger: 2,3 (both the same)
        // Right trigger: 6,7 (both the same)
        // Dpad: y=8, x=9
        x_axis = 4;
        y_axis = 1;
    }

    /**
    * Indicates if the intializeDevice() call has been invoked.
    */
    public boolean isInitialized()
    {
        return isInitialized_;
    }

    /**
    * Called the first time the user actually hits one of the joystick buttons.
    * @param device - the Android InputDevice instance representing the
    * joystick
    */
    public void initializeDevice(InputDevice device)
    {
        device_ = device;

        //Determine number of axis available
        int numAxes = 0;
        for (MotionRange range : device.getMotionRanges())
        {
            if ((range.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) 
                numAxes += 1;
        }

        //Allocate storage for axes and key values
        axes_        = new int[numAxes];          //Each value indicates axis ID
        axesValues_  = new float[numAxes];        //One-to-one correspondence with axes_, contains latest value for each axis
        keys_        = new SparseIntArray();      //Maps buttonID to current state.

        //Determine axis IDs and store into axes_ member
        int i = 0;
        for (MotionRange range : device.getMotionRanges())
        {
            if ((range.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) 
            {
               axes_[i++] = range.getAxis();
            }
        }    

        isInitialized_ = true;    
    }

    /**
    * Rounds a number to zero if it's close enough to zero. Mainly used
    * used to make sure fields in sensor_msgs/Joy don't look like
    * 1.336346346E-10
    */
    private float roundToZeroIfNecessary(float val)
    {
        final float UPPER_THRESHOLD = 0.01f;
        if (Math.abs(val) < UPPER_THRESHOLD)
            return 0.0f;
        return val;
    }
    
    /**
    * This method should be called from a holding widget that can intercept
    * Android input events when a joystick axis control is manipulated
    */
    public boolean onJoystickMotion(MotionEvent event) 
    {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) == 0)
        {
            return false;
        }

        //Update the latest axis value for each axis. The MotionEvent object contains the state of each axis.
        for (int i = 0; i < axes_.length; i++)
        {
            int axisId = axes_[i];
            float axisVal = roundToZeroIfNecessary(event.getAxisValue(axisId));
            axesValues_[i] = axisVal;
        }

        // TODO: send/publish joystick event
        Log.d("JoystickNode", "Joystick update. x: " + axesValues_[x_axis] + ", y: " + axesValues_[y_axis]);

        return true;
    }
}
