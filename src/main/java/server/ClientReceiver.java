package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class ClientReceiver extends Thread {
    
    private InetAddress group;
    private MulticastSocket socket;
    
    public ClientReceiver(InetAddress group) throws IOException {
        this.group = group;
        this.socket = new MulticastSocket(4445);
        
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || !iface.isUp())
                continue;
            
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                socket.setInterface(addr);
                socket.joinGroup(group);
            }
        }
        
    }
    
    @Override
    public void run() {
        super.run();
        while (true) {
            try {
                byte[] buf = new byte[256];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                
                String received = new String(packet.getData());
                System.out.println("Server replied -> " + received);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        
    }
}