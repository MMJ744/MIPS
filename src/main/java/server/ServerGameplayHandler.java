package server;

import utils.Input;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class ServerGameplayHandler {
    
    private Queue<String> outgoingQueue;
    private Queue<String> incomingQueue;
    
    private Queue<String> positionQueue;
    private Queue<String> collisionQueue;
    private BlockingQueue<Integer> keypressQueue;
    
    private Thread outgoingPacketManager;
    private Thread incomingPacketManager;
    
    private PacketSender sender;
    private PacketReceiver receiver;
    
    private ArrayList<InetAddress> ipStore;
    
    private int playerCount;
    
    public ServerGameplayHandler(ArrayList<InetAddress> ips, int numPlayers) throws IOException {
        
        outgoingQueue = new ConcurrentLinkedQueue<String>();
        incomingQueue = new ConcurrentLinkedQueue<String>();
        keypressQueue = new LinkedBlockingDeque<>();
        this.positionQueue = new LinkedBlockingDeque<>();
        this.collisionQueue = new LinkedBlockingDeque<>();
        this.playerCount = numPlayers;
        initialisePacketManagers();
        
        outgoingQueue.add("POS");
        this.ipStore = ips;
        this.sender = new PacketSender(NetworkUtility.CLIENT_DGRAM_PORT, this.outgoingQueue, ipStore);
        this.receiver = new PacketReceiver(NetworkUtility.SERVER_DGRAM_PORT, this.incomingQueue);
        this.incomingPacketManager.start();
        this.outgoingPacketManager.start();
        this.sender.start();
        this.receiver.start();
    }
    
    public int getPlayerCount() {
        return this.playerCount;
    }
    
    public void sendPacket(Input i) {
        this.outgoingQueue.add(i.toString());
    }
    
    public static void main(String[] args) throws IOException {

//        ServerGameplayHandler s = new ServerGameplayHandler();
        System.out.println("ServerGameplayHandler Running");
    }
    
    /**
     * Initialises the packet managers
     */
    private void initialisePacketManagers() {
        // puts inputs from queues into the outgoing queue - not sure this one closes
        this.incomingPacketManager =
                new Thread() {
                    public void run() {
                        Integer key;
                        while (!isInterrupted()) {
                            if (incomingQueue.isEmpty()) {
                                continue;
                            }
                            System.out.println(incomingQueue.peek());
                            outgoingQueue.add(incomingQueue.poll());
                            //                    key = Integer.valueOf(incomingQueue.poll());
                            //                    keypressQueue.add(key);
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
        
        // reads inputs from the incoming queue into the relevant queue - position queue or collision
        // queue
        // could be parallelised in adding positions and collisions to the queue - hogging was unlikely
        // as collisions
        // occur a lot less than position updates
        this.outgoingPacketManager =
                new Thread() {
                    public void run() {
                        while (!isInterrupted()) {
                            try {
                                if (positionQueue.isEmpty()) {
                                    continue;
                                } else {
                                    String data = collisionQueue.poll();
                                    data = NetworkUtility.COLLISIONS_CODE + data;
                                    outgoingQueue.add(data);
                                }
                                if (positionQueue.isEmpty()) {
                                    continue;
                                } else {
                                    String data = positionQueue.poll();
                                    data = NetworkUtility.POSITION_CODE + data;
                                    outgoingQueue.add(data);
                                }
                                Thread.sleep(50);
                                
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
  }
}