/*******************************************************************************
 * Copyright (c) 2017 Ash (QuarkTheAwesome) & Maschell
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *******************************************************************************/
package net.ash.HIDToVPADNetworkClient.controller;

import lombok.Getter;
import lombok.Synchronized;
import net.ash.HIDToVPADNetworkClient.exeption.ControllerInitializationFailedException;
import net.ash.HIDToVPADNetworkClient.manager.ControllerManager;
import net.ash.HIDToVPADNetworkClient.util.Settings;
import net.ash.HIDToVPADNetworkClient.util.Utilities;

/**
 * Main controller interface, extended by controller drivers.
 * <br><br>
 * See {@link LinuxDevInputController} for a full implementation.
 * @author ash
 */
public abstract class Controller implements Runnable{
    private boolean active;
    @Getter private final ControllerType type;
    @Getter private final String identifier;
    private byte[] latestData = null;
        
    boolean shutdown = false;
    boolean shutdownDone = false;
    private Object dataLock = new Object();
    private Object shutdownLock = new Object();
    
    private Object rumbleLock = new Object();
    private boolean rumble = false;
        
    public Controller(ControllerType type, String identifier) throws ControllerInitializationFailedException{
        this.type = type;
        this.identifier = identifier;
        if(!initController(identifier)){
            throw new ControllerInitializationFailedException();
        }
    }
    
    @Override
    public void run() {
        boolean shutdownState = shutdown;
        while(!shutdownState){
            Utilities.sleep(Settings.getDetectControllerInterval());
            while(isActive()) {
                byte[] newData =  pollLatestData();
                if(newData != null){
                    setLatestData(newData);
                }
                doSleepAfterPollingData();
            }
            synchronized (shutdownLock) {
                shutdownState = shutdown;
            }
        }
        synchronized (shutdownLock) {
            shutdownDone = true;
        }
    }
    
    protected void doSleepAfterPollingData() {
        Utilities.sleep(Settings.getSleepAfterPolling());
    }

    @Synchronized("dataLock")
    private void setLatestData(byte[] newData) {
        this.latestData = newData;      
    }
    
    @Synchronized("dataLock")
    public byte[] getLatestData() {
        if(latestData != null){
            byte[] data = this.latestData.clone();
            this.latestData = null;
            return data;
        }else{
            return null;
        }
    }

    public abstract byte[] pollLatestData();
    
	/**
	 * Sets up the driver.
	 * <br>
	 * During this method call, a connection will be made with the controller hardware (if required).
	 * 
	 * @param arg Driver-specific init argument, see {@link ControllerManager} and {@link ControllerDetector}.
	 * @return Whether initialization was successful.
	 */
	public abstract boolean initController(String identifier);

	
	/**
	 * Destroys the controller driver and ends the polling thread.
	 */
	public void destroyAll(){
	    destroyDriver();
	    endThread();
	}
	
	/**
     * Destroys the controller driver.
     */
	public abstract void destroyDriver();
    
	
	private void endThread() {
	    new Thread(new Runnable() {
            @Override
            public void run() {
                setActive(false);
                
                synchronized (shutdownLock) {
                    shutdown = true;
                }
               
                boolean done = false;
                int i = 0;
                while(!done){
                    synchronized (shutdownLock) {
                        done = shutdownDone;
                    }
                    Utilities.sleep(50);
                    if(i++ > 50) System.out.println("Thread doesn't stop!!");
                }
            }
        }).start();
    }

    public abstract short getVID();
    
    public abstract short getPID();

	@Synchronized("shutdownLock")
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
    
    @Override
    public String toString(){
        return getType() + " " + getIdentifier();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Controller other = (Controller) obj;
        if (identifier == null) {
            if (other.identifier != null)
                return false;
        } else if (!identifier.equals(other.identifier))
            return false;
        if (type != other.type)
            return false;
        return true;
    }
    
    @Synchronized("rumbleLock")
    public boolean isRumble() {
        return rumble;
    }

    @Synchronized("rumbleLock")
    public void startRumble() {
        this.rumble = true;
    }
    
    @Synchronized("rumbleLock")
    public void stopRumble() {
        this.rumble = false;
    }

    public enum ControllerType {
        PureJAVAHid, LINUX, XINPUT13,XINPUT14
    }
    
    public abstract String getInfoText();
}