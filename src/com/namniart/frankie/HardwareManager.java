package com.namniart.frankie;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * Interface thread to the robot hardware. Runs as a thread, receives periodic updates from the robot
 * and updates its internal state accordingly. <br/>
 * <br/>
 * General usage pattern: <br/>
 * Create an instance of the class, giving it a BluetoothDevice and a Handler to send output to. <br/>
 * call start(). <br/>
 * call getCurrentState() any time to get a copy of the current robot state <br/>
 */
/*
 * Serial communication protocol:
 * This is mostly documentation of the internals; users of this class don't have to read it.
 * 
 * Basis: the serial protocol focuses on piecemeal updates as new data becomes available from the robot
 * each update is preceded by an identifier, and followed by a null byte
 * 
 * Input sentences:
 *
 * Output sentences:
 * Speed: 'M[N]'
 * 	S: identifier
 * 	N: target speed (1 byte, signed)
 * 
 * Direction: 'D[N]'
 * 	D: identifier
 * 	N: steering setting (1 byte, signed, will require calibration)
 * 
 * 
 * @author Austin Hendrix
 *
 */

public class HardwareManager extends Thread {
	
	public static final byte MAX_SPEED = (byte)100;
    public static final byte MAX_HEADING = (byte)100;
	
	private BluetoothDevice mDevice;
	private boolean mStop;
	private RobotApplication mApp;
	
	
	// robot output variables
	private Boolean mUpdateSent;
	private byte mSpeed;
	private byte mDirection;
	private boolean mShutdown;
	private boolean mAutonomous;
	
	// raw bytes that the application has requested to send
	private List<Packet> mPackets;
		
	/**
	 * Create a hardware manager instance. Talk to the robot on the other of socket s, send status messages
	 * to master.
	 * @param d Bluetooth device to talk to
	 */
	public HardwareManager(BluetoothDevice d, RobotApplication app) {
		mDevice = d;
		mStop = false;
		mApp = app;
		
		mPackets = new LinkedList<Packet>();
	}
	
	private void message(String msg) {
		Log.d("HardwareManager", msg);
	}
	
	/**
	 * Run the hardware manager. Don't call this directly; invoke the superclass start() so that it runs
	 * as its own thread.
	 */
	@Override
	public void run() {
		BluetoothDevice dev = mDevice;
		try {
			
			// well-know UUID for SPP; from Android documentation: 
			// http://developer.android.com/reference/android/bluetooth/BluetoothDevice.html#createRfcommSocketToServiceRecord%28java.util.UUID%29
			BluetoothSocket socket = mDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
			message("Created socket to " + mDevice.getName());

			// connect socket
			message("Connecting socket to " + dev.getName() + "...");
			socket.connect();
			message("Connected to " + dev.getName());
			
			// set up input and output streams
			OutputStream out = socket.getOutputStream();
			InputStream in = socket.getInputStream();
			int c = 0;
			int i;
			
			// main thread loop
			while( mStop != true ) {
				if( in.available() < 1 
						&& !mStop ) sleep(10); // this limits how quickly we can send/receive updates from the hardware
				
				if( in.available() > 0 ) {
					//message("Receiving data");
					int type = in.read(); // read type
					//message("Handling packet: " + type);
					List<Byte> data = new LinkedList<Byte>();
					do {
						c = in.read();
						data.add((byte)c);
						//message("Got byte: " + c);
					} while(c != '\r');
					Packet p = new Packet(data);
					List<PacketHandler> handlers = mApp.getHandlers(type);
					if( handlers != null ) {
						for( PacketHandler h : mApp.getHandlers(type) ) {
							h.handlePacket(p);
						}
					}
				}
								
				// send any packets requested by the application
				synchronized(mPackets) {
					for( Packet p : mPackets ) {
						message("Transmitting packet: " + p.toString());
						out.write(p.toByteArray());
					}
					mPackets.clear();
				}
			}
						
			// don't forget to close our socket when we're done.
			socket.close();
			message("HardwareManager terminated");
			
		} catch(Exception e) {
			// tell the master why we died
			Log.e("HardwareManager", "Exception: " + e.toString(), e);
		}
		return; // I like seeing where the end of my function is
	}
	
	/**
	 * Request that this thread stop.
	 */
	public void sendStop() {
		mStop = true;
	}
			
	/**
	 * send a packet to the robot
	 */
	public void sendPacket(Packet p) {
		synchronized(mPackets) {
			message("Put packet in queue");
			mPackets.add(p);
		}
	}
}