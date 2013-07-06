package com.bratner.hid;

import java.io.IOException;
import java.io.InputStreamReader;

import com.codeminders.hidapi.HIDDevice;
import com.codeminders.hidapi.HIDDeviceInfo;
import com.codeminders.hidapi.HIDManager;

public class App {

	/* vendor and product ids for duinobot v1.2 */
	static private int db12pid = 0x204f;
	static private int db12vid = 0x03eb;
	static boolean listening;
    
	public static class ReaderThread implements Runnable {
		private HIDDevice dev;		
        private InputStreamReader ir;
		public ReaderThread(HIDDevice dev) {
			this.dev = dev;
			ir = new InputStreamReader(System.in);
		}

		@Override
		public void run() {
			byte buf[] = new byte[128];
			int len = 0;
			while (listening) {
				/* check if there is anything on stdin */
				try {
					if( ir.ready() ){
						 
                         // Keep this when duinobot HID.cpp will be fixed to handle 8byte packets				
						/* byte data[] = new byte[8];
						data[0] = (byte)0xb1;
						int i =0;
						for(i = 1; i<8;i++){
							if(!ir.ready())
								break;
							data[i]=(byte)ir.read();
						}
					    if(i<7)
					    	data[i]=0;
					    	*/
						byte data[] = new byte[2];
						data[0] = (byte)0xb1;
						data[1] = (byte)ir.read();
						dev.write(data);						
					}					
				} catch (IOException e1) {
				    System.err.println("ReaderThread: problem with console input");				
				}
				
				
			
				try {
					len = dev.read(buf);
					if (len != 8)
						continue;
				} catch (Exception e) {						
					    /* This is the reason why reader thread can silently die */
						Thread.currentThread().interrupt();						
					    break;//Stop doing whatever I am doing and terminate					
			    }
				System.out.print(parsePacket(buf));
			}

		}

	}

	public static class DeviceFinder implements Runnable {

		private HIDManager hm;
		//private boolean listening;
		private HIDDeviceInfo devices[];
		private HIDDevice dev;
		private Thread reader;

		public DeviceFinder(HIDManager hm) {
			this.hm = hm;
			listening = false;
			devices = null;
		}

		@Override
		public void run() {

			while (!Thread.interrupted()) {
				try {
				    devices = hm.listDevices();
				} catch (Exception e) {
					System.err.print("Problem listing devices " + e);				
				}
				if(devices == null ){
					listening = false;
					if(reader != null && reader.isAlive())
					try {
					    reader.join();					
					} catch (InterruptedException e) {
						System.err.println("DeviceFinder: Failed to wait for ReaderThread to finish. "+e);
					}
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						System.err.println("Thread sleep was interrupted.");
					}
				    continue;				   
				} 				
				boolean detected = false;
				for (int i = 0; i < devices.length; i++) {
					
				    int vid = devices[i].getVendor_id();
				    int pid = devices[i].getProduct_id();					
					if (vid == db12vid && pid == db12pid) {
						detected = true;
						if (!listening || reader.getState().name() == "TERMINATED") {
							
							try {
								dev = devices[i].open();
								if(!listening){ 
								    System.err.println("DuinoBot connected.");
								    /* re-connections are silent */
								}
								reader = new Thread(new ReaderThread(dev));
								reader.start();
								listening = true;								
							} catch (IOException e) {
								System.err.print("Unable to open the deivce: "
										+ e);
								listening = false;
							}
						}
					}
				} // for devices		
				
				if(!detected && listening) {
					System.err.println("DuinoBot disconnected.");
					listening=false;					
					try {
						    reader.join();					
					} catch (InterruptedException e) {
							System.err.println("DeviceFinder: Failed to wait for ReaderThread to finish. "+e);
					}
					
				}				
				try {
					Thread.sleep(100);					
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
			if (b[i] == (byte)'\r')
				continue;
			s += (char) b[i];
		}

		return s;
	}

	static public void main(String[] args) {
		HIDManager hmi = null;
		listening = false;
		com.codeminders.hidapi.ClassPathLibraryLoader.loadNativeHIDLibrary();

		try {
			hmi = HIDManager.getInstance();
		} catch (Exception e) {
			System.out.print("HMI " + e);
			Runtime.getRuntime().exit(3);
		}
		System.err.println("Running DeviceFinder thread...");
		Thread dfthread = new Thread(new DeviceFinder(hmi));
		dfthread.start();
	}
}
