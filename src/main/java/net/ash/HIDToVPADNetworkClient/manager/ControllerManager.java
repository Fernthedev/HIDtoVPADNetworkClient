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
package net.ash.HIDToVPADNetworkClient.manager;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


import com.github.strikerx3.jxinput.XInputDevice;
import com.github.strikerx3.jxinput.XInputDevice14;
import com.github.strikerx3.jxinput.exceptions.XInputNotLoadedException;
import lombok.Synchronized;
import lombok.extern.java.Log;
import net.ash.HIDToVPADNetworkClient.controller.Controller;
import net.ash.HIDToVPADNetworkClient.controller.Controller.ControllerType;
import net.ash.HIDToVPADNetworkClient.controller.HidController;
import net.ash.HIDToVPADNetworkClient.controller.LinuxDevInputController;
import net.ash.HIDToVPADNetworkClient.controller.XInput13Controller;
import net.ash.HIDToVPADNetworkClient.controller.XInput14Controller;
import net.ash.HIDToVPADNetworkClient.controller.XInputController;
import net.ash.HIDToVPADNetworkClient.exeption.ControllerInitializationFailedException;
import net.ash.HIDToVPADNetworkClient.hid.HidDevice;
import net.ash.HIDToVPADNetworkClient.hid.HidManager;
import net.ash.HIDToVPADNetworkClient.util.MessageBox;
import net.ash.HIDToVPADNetworkClient.util.MessageBoxManager;
import net.ash.HIDToVPADNetworkClient.util.Settings;

@Log
public final class ControllerManager {
    private static final Map<String, Controller> attachedControllers = new HashMap<>();

    private static boolean threwUnsatisfiedLinkError = false;

    private ControllerManager() {
        // Utility Class
    }

    /**
     * Detects all attached controller.
     */

    public static void detectControllers() {
        Map<String, ControllerType> connectedDevices = new HashMap<String, ControllerType>();

        if (Settings.isLinux()) {
            connectedDevices.putAll(detectLinuxControllers());
        } else if (Settings.isWindows()) {
            connectedDevices.putAll(detectXInputControllers());
        }

        connectedDevices.putAll(detectHIDDevices());

        // Remove detached devices
        List<String> toRemove = new ArrayList<String>();
        synchronized (attachedControllers) {
            for (String s : attachedControllers.keySet()) {
                if (!connectedDevices.containsKey(s)) {
                    toRemove.add(s);
                }
            }
        }

        for (String remove : toRemove) {
            synchronized (attachedControllers) {
                attachedControllers.get(remove).destroyAll();
                attachedControllers.remove(remove);
                log.info("Device removed: " + toRemove);
            }
        }

        // Add attached devices!
        for (Entry<String, ControllerType> entry : connectedDevices.entrySet()) {
            String deviceIdentifier = entry.getKey();
            boolean contains;
            synchronized (attachedControllers) {
                contains = attachedControllers.containsKey(deviceIdentifier);
            }
            if (!contains) {
                Controller c = null;
                switch (entry.getValue()) {
                case HIDController:
                    try {
                        c = HidController.getInstance(deviceIdentifier);
                    } catch (ControllerInitializationFailedException e) {
                        log.info(e.getMessage());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case LINUX:
                    try {
                        c = new LinuxDevInputController(deviceIdentifier);
                    } catch (ControllerInitializationFailedException e) {
                        log.info(e.getMessage());
                    }
                    break;
                case XINPUT14:
                    try {
                        c = new XInput14Controller(deviceIdentifier);
                    } catch (ControllerInitializationFailedException e) {
                        log.info(e.getMessage());
                    }
                    break;
                case XINPUT13:
                    try {
                        c = new XInput13Controller(deviceIdentifier);
                    } catch (ControllerInitializationFailedException e) {
                        // e.printStackTrace();
                    }
                    break;
                default:
                    break;
                }
                if (c != null) { // I don't like that starting the Thread happens here =/
                    if (Settings.AUTO_ACTIVATE_CONTROLLER) {
                        c.setActive(true);
                    }
                    new Thread(c, "Controller Thread " + deviceIdentifier).start();
                    synchronized (attachedControllers) {
                        attachedControllers.put(deviceIdentifier, c);
                    }
                    log.info("Device added: " + deviceIdentifier);
                }
            }
        }
    }

    @Synchronized("attachedControllers")
    public static List<Controller> getAttachedControllers() {
        return new ArrayList<>(attachedControllers.values());
    }

    private static Map<String, ControllerType> detectHIDDevices() {
        Map<String, ControllerType> connectedDevices = new HashMap<>();
        for (HidDevice info : HidManager.getAttachedControllers()) {
            String path = info.getPath();
            connectedDevices.put(path, ControllerType.HIDController);
        }

        return connectedDevices;
    }

    private static Map<String, ControllerType> detectXInputControllers() {
        Map<String, ControllerType> result = new HashMap<>();
        if (!Settings.ControllerFiltering.getFilterState(Settings.ControllerFiltering.Type.XINPUT)) return result;

        ControllerType type = ControllerType.XINPUT13;

        // Try and catch missing C++ redist
        try {
            XInputDevice.isAvailable();
        } catch (UnsatisfiedLinkError e) {
            if (!threwUnsatisfiedLinkError) {
                e.printStackTrace();
                log.info("This error can be fixed! Please install the Visual C++ Redistributables:");
                log.info("https://www.microsoft.com/en-us/download/details.aspx?id=48145");
                log.info("If that doesn't help, create an issue on GitHub.");
                MessageBoxManager.addMessageBox(
                        "There was a problem setting up XInput.\nTo fix this, try installing the Visual C++\nredistributables: https://tinyurl.com/vcredist2015.\n\nOther controller types should still work.",
                        MessageBox.MESSAGE_ERROR);
                threwUnsatisfiedLinkError = true;
            }
        }

        if (XInputDevice.isAvailable() || XInputDevice14.isAvailable()) {
            if (XInputDevice14.isAvailable()) {
                type = ControllerType.XINPUT14;
            }
            for (int i = 0; i < 4; i++) {
                XInputDevice device;
                try {
                    device = XInputDevice.getDeviceFor(i);
                    try {
                        if (device.poll() && device.isConnected()) { // Check if it is this controller is connected
                            result.put(XInputController.XINPUT_INDENTIFER + i, type);
                        }
                    } catch (BufferUnderflowException e) {
                        //
                        log.info("XInput error.");
                    }
                } catch (XInputNotLoadedException e) {
                    // This shouln't happen?
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    private static Map<String, ControllerType> detectLinuxControllers() {
        Map<String, ControllerType> result = new HashMap<>();
        if (!Settings.ControllerFiltering.getFilterState(Settings.ControllerFiltering.Type.LINUX)) return result;

        File devInput = new File("/dev/input");
        if (!devInput.exists()) return result;

        File[] linuxControllers = devInput.listFiles((dir, name) -> {
            return name.startsWith("js"); // js0, js1, etc...
        });

        for (File controller : linuxControllers) {
            result.put(controller.getAbsolutePath(), ControllerType.LINUX);
        }

        return result;
    }

    public static List<Controller> getActiveControllers() {
        List<Controller> active = new ArrayList<Controller>();
        List<Controller> attached = getAttachedControllers();
        for (Controller c : attached) {
            if (c.isActive()) {
                active.add(c);
            }
        }
        return active;
    }

    public static void deactivateAllAttachedControllers() {
        List<Controller> attached = getAttachedControllers();
        for (Controller c : attached) {
            c.setActive(false);
        }
    }
}
