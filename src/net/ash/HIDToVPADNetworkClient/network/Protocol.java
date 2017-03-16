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
package net.ash.HIDToVPADNetworkClient.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import lombok.extern.java.Log;
import net.ash.HIDToVPADNetworkClient.network.commands.AttachCommand;
import net.ash.HIDToVPADNetworkClient.network.commands.DetachCommand;
import net.ash.HIDToVPADNetworkClient.network.commands.PingCommand;
import net.ash.HIDToVPADNetworkClient.network.commands.ReadCommand;

@Log
public class Protocol {
	public static final int TCP_PORT = 8112;
	public static final int UDP_PORT = 8113;
	
	public static final byte TCP_HANDSHAKE = 0x12;
	public static final byte TCP_SAME_CLIENT = 0x20;
	public static final byte TCP_NEW_CLIENT = 0x21;
	
	public static final byte TCP_CMD_ATTACH = 0x01;
	public static final byte TCP_CMD_DETACH = 0x02;
	public static final byte TCP_CMD_PING = (byte)0xF0;
	
	public static final byte UDP_CMD_DATA = 0x03;
	
	
	public static final byte TCP_CMD_ATTACH_CONFIG_FOUND =      (byte) 0xE0;
    public static final byte TCP_CMD_ATTACH_CONFIG_NOT_FOUND =  (byte) 0xE1;
    public static final byte TCP_CMD_ATTACH_USERDATA_OKAY =     (byte) 0xE8;
    public static final byte TCP_CMD_ATTACH_USERDATA_BAD =      (byte) 0xE9;
	
	private Protocol(){}
	
	public enum HandshakeReturnCode {
        BAD_HANDSHAKE,
        SAME_CLIENT,
        NEW_CLIENT
    }
	
	public static byte[] getRawAttachDataToSend(AttachCommand command) throws IOException {
        return ByteBuffer.allocate(9)
                .put(Protocol.TCP_CMD_ATTACH)
                .putInt(command.getHandle())
                .putShort(command.getVid())
                .putShort(command.getPid())
                .array();
    }
	
	public static byte[] getRawDetachDataToSend(DetachCommand command) throws IOException {
        return ByteBuffer.allocate(5)
                .put(Protocol.TCP_CMD_DETACH)
                .putInt(command.getHandle())
                .array();
    }
    
	public static byte[] getRawPingDataToSend(PingCommand command){
        return new byte[]{Protocol.TCP_CMD_PING};
    }   

	public static byte[] getRawReadDataToSend(List<ReadCommand> readCommands) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeByte(Protocol.UDP_CMD_DATA);
        dos.writeByte(readCommands.size());
        
        for(ReadCommand command : readCommands){
            NetworkHIDDevice sender = command.getSender();            
            byte[] data = command.getData();
            if(data.length > 0xFF){
                log.info("Tried to send too much data. Maximum is 0xFF bytes read command.");
                continue;
            }

            byte newLength = (byte)(data.length & 0xFF);
           
            dos.writeInt(command.getHandle());                  
            dos.writeShort(sender.getDeviceslot());
            dos.writeByte(sender.getPadslot());
            
            dos.write(newLength);
            dos.write(data, 0, newLength);
        }
        
        return bos.toByteArray();
    }
}
