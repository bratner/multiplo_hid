package com.bratner.hid;

import java.io.IOException;
import com.codeminders.hidapi.HIDDevice;
import com.codeminders.hidapi.HIDDeviceInfo;
import com.codeminders.hidapi.HIDManager;

public class App {

	/* vendor and product ids for duinobot v1.2 */
	static private int db12pid = 0x204f;
	static private int db12vid = 0x03eb;
    
	public static class ReaderThread implements Runnable {
		private HIDDevice dev;

		public ReaderThread(HIDDevice dev) {
			this.dev = dev;
		}

		@Override
		public void run() {
			byte buf[] = new byte[128];
			int len = 0;
			while (Thread.currentThread().isAlive()) {
				try {
					len = dev.read(buf);
					if (len != 8)
						continue;
				} catch (IOException e) {											    
					    return;//Stop doing whatever I am doing and terminate					
			
				}
				System.out.print(parsePacket(buf));
			}

		}

	}

	public static class DeviceFinder implements Runnable {

		private HIDManager hm;
		private boolean detected;
		private HIDDeviceInfo devices[];
		private HIDDevice dev;
		private Thread reader;

		public DeviceFinder(HIDManager hm) {
			this.hm = hm;
			detected = false;
			devices = null;
		}

		@Override
		public void run() {

			while (true) {
				try {
					devices = hm.listDevices();
				} catch (Exception e) {
					System.err.print("List " + e);
					System.exit(3);
				}
				for (int i = 0; i < devices.length; i++) {
					int vid = devices[i].getVendor_id();
					int pid = devices[i].getProduct_id();
					if (vid == db12vid && pid == db12pid) {
						if (!detected) {
							detected = true;
							try {
								dev = devices[i].open();
								System.err.println("Device connected.");
								reader = new Thread(new ReaderThread(dev));
								reader.start();
								
							} catch (IOException e) {
								System.err.print("Unable to open the deivce: "
										+ e);
								detected = false;
								devices = null;
							}
							break;
						} else {
							if (reader.getState() == Thread.State.TERMINATED){
								detected = false;								
								System.err.println("Device disconnected.");							
							}
						}// detected
					} else{ //can't find
						if(detected){
							try {
								reader.join();
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								
							}
							detected = false;
							System.err.println("Device disconnected.");
							
						}
						
					}
				} // for devices
				try {
					Thread.sleep(200);// milis
				} catch (InterruptedException e) {
					System.err.print(e);
				}
			} // while
		}

	}

	static public String parsePacket(byte b[]) {
		String s = "";
		if (b[0] != (byte) 0xb1)
			return s;
		for (int i = 1; i < 8; i++) {
			if (b[i] == 0)
				break;
			s += (char) b[i];
		}

		return s;
	}

	static public void main(String[] args) {
		HIDManager hmi = null;

		com.codeminders.hidapi.ClassPathLibraryLoader.loadNativeHIDLibrary();

		try {
			hmi = HIDManager.getInstance();
		} catch (Exception e) {
			System.out.print("HMI " + e);
			Runtime.getRuntime().exit(3);
		}
		Thread dfthread = new Thread(new DeviceFinder(hmi));
		dfthread.start();
	}
}
