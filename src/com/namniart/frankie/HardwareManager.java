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
		
		mShutdown = false;
		mUpdateSent = false;
		mAutonomous = false;
		mSpeed = 0;
		mDirection = 120;
		
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
				while( in.available() < 1 
						&& mUpdateSent 
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
				
				if( !mUpdateSent ) {
					synchronized(mUpdateSent) {
						mUpdateSent = true;
						if( mShutdown ) {
							byte data[] = new byte[10];
							for( i=0; i<9; i++) data[i] = 'Z';
							data[9] = '\r';
							out.write(data);
						} else {
							Packet cmd = new Packet('C');
							
							if( mAutonomous ) {
								cmd.append((byte)2); // autonomous mode
								cmd.append((byte)0); // zero speed
								cmd.append((byte)0); // zero steer angle
							} else {
								cmd.append((byte)1); // remote mode
								cmd.append(mSpeed);
								cmd.append(mDirection);
							}
							cmd.finish();
							out.write(cmd.toByteArray());
						}
					}
					//message("Update sent");
				}
				
				// send any packets requested by the application
				synchronized(mPackets) {
					for( Packet p : mPackets ) {
						message("Transmitting packet");
						out.write(p.toByteArray());
					}
					mPackets.clear();
				}
			}
			
			// hardcoded stop on thread end
			// TODO: update this for packets
			out.write('C');
			out.write(0);
			out.write(0);
			out.write(0);
			out.write('\r');
			
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
	 * Set the robot speed
	 * @param s target speed
	 */
	public void setSpeed(byte s) {
		synchronized (mUpdateSent) {
			mUpdateSent = false;
			mSpeed = s;
		}
	}
	
	public byte getSpeed() {
		return mSpeed;
	}
	
	/**
	 * Set the position of the robot's steering mechanism; positive is right
	 * @param dir target steering position in arbitrary units
	 */
	public void setDirection(byte dir) {
		synchronized (mUpdateSent) {
			mUpdateSent = false;
			mDirection = dir;
		}
	}
	
	public byte getDirection() {
		return mDirection;
	}
		
	public void fullStop() {
		synchronized (mUpdateSent) {
			mUpdateSent = false;
			mSpeed = 0;
			mDirection = 0;
		}
	}
	
	/**
	 * Send a shutdown command to the robot
	 */
	public void setShutdown() {
		synchronized(mUpdateSent) {
			mUpdateSent = false;
			mShutdown = true;
		}
	}
	

	/**
	 * set whether the robot is in autonomous mode
	 * @param a: true to put robot into autonomous mode
	 */
	public void setAutonomous(boolean a) {
		if( a != mAutonomous ) {
			synchronized(mUpdateSent) {
				mUpdateSent = false;
				mAutonomous = a;
			}		
		}
	}
		
	/**
	 * send a packet to the robot
	 */
	public void sendPacket(Packet p) {
		synchronized(mPackets) {
			mPackets.add(p);
		}
	}
}