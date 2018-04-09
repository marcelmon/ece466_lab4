
import java.io.*;
import java.net.*;

import java.util.*;

import java.lang.Long;

import java.util.AbstractMap.SimpleEntry;


import java.nio.ByteBuffer;


import java.util.HashMap;

public class Estimator {



    public static void main(String[] args) throws IOException, Exception {

        new Estimator().run();
    }

    /**
    * Converts a byte array to an integer.
    * @param value a byte array 
    * @param start start position in the byte array
    * @param length number of bytes to consider
    * @return the integer value
    */
    public static int fromByteArray(byte[] value, int start, int length) {
        int Return = 0;
        for(int i=start; i< start+length; i++) {
            Return = (Return << 8) + (value[i] & 0xff);
        }
        return Return;
    }



    /** 
    * Converts an integer to a byte array. 
    * @param      value   an integer 
    * @return      a byte array representing the integer 
    */
    public static byte[] toByteArray(int value) {
        byte[] Result = new byte[4];
        Result[3] = (byte) ((value >>> (8*0)) & 0xFF);
        Result[2] = (byte) ((value >>> (8*1)) & 0xFF); 
        Result[1] = (byte) ((value >>> (8*2)) & 0xFF); 
        Result[0] = (byte) ((value >>> (8*3)) & 0xFF); 
        return Result;
    }



    public class ReceiverRunnable implements Runnable {




        



        public long firstSendTimeNano;


        public int port;

        public DatagramSocket socket;
        public DatagramPacket packet;

        public byte[] buf;

        public PrintStream pout;

        public HashMap<Integer, Long> packetData;

        public ReceiverRunnable(HashMap<Integer, Long> packetData, int port, String outfile){
            
            this.packetData = packetData;

            this.port = port;
            try{
                socket = new DatagramSocket(port);
            } catch (SocketException e){
                System.out.println("ReceiverRunnable : Socket error " + e.getMessage());
            }

            buf = new byte[2000];

            try{
                FileOutputStream fout =  new FileOutputStream(outfile);
                pout = new PrintStream (fout);
            }
            catch(IOException e){
                System.out.println("caught io in ReceiverRunnable : " + e.getMessage());
            }


            packet = new DatagramPacket(buf, buf.length);
        }

        public void run(){

            

            long currentArrivalTimeNano;

            long currentArrivalTimeMicroFromStart = 0;


            long firstSendTimeNano = 0L;
            while(true){


                try{
                    socket.receive(packet);
                }
                catch(IOException e){
                    System.out.println("caught socket io in ReceiverRunnable : " + e.getMessage());
                }
                
                currentArrivalTimeNano = System.nanoTime();

                if(firstSendTimeNano == 0L){
                    firstSendTimeNano = packetData.get(1);
                }
                
                int seqNum = fromByteArray(packet.getData(), 2, 4);

                long packetSendTimeNano = packetData.get(seqNum);

                long packetSendTimeMicroNormal = (packetSendTimeNano - firstSendTimeNano)/1000;

                long packetReceiveTimeMicroNormal = (currentArrivalTimeNano - firstSendTimeNano)/1000;

                pout.println(seqNum + "\t"+ packetSendTimeMicroNormal + "\t" + packetReceiveTimeMicroNormal); 

                if(seqNum % 1000 == 0){
                    System.out.println("RECEIVED "+ seqNum+" packets");
                }
                // if(SeqNo + intervalSize >= currentInterval*intervalSize){
                //  System.out.println("Sequence number " + SeqNo + "\t:\t" + p.getAddress().getHostName() + "\t:\tlength is " + p.getLength() + "\t:\tTime since last arrival " + (int)((currentArrivalTime - previousArrivalTimeNano)/1000) + " us" );
                //  currentInterval++;
                // }
               
            }
        }
    }










    public class SenderRunnable implements Runnable{



        public int totalPackets;

        public long nanoSecPacketSpacing;

        public int packetSize;

        public int returnPort;

        public InetAddress destAddr;

        public int destPort;


        public ReceiverRunnable receiverRunnable;



        public void busyWaitNanos(long nanos){
            long waitUntil = System.nanoTime() + nanos;
            while(waitUntil > System.nanoTime()){
                ;
            }
        }



        


        public HashMap<Integer, Long> packetData;

        public SenderRunnable(HashMap<Integer, Long> packetData, int packetSize_byte, int returnPort, int avgBitRate_kbps, InetAddress destAddr, int destPort, int totalPackets){

            this.packetData = packetData;

            int packetSize_bits = packetSize_byte*8;
            int avgBitRate_bps = avgBitRate_kbps * 1000;
            double timeBetweenPacketsSecond = (double) packetSize_bits/(double) avgBitRate_bps;
            long timeBetweenPacketsNanoSecond = (long) (timeBetweenPacketsSecond * 1000000000);
            nanoSecPacketSpacing = timeBetweenPacketsNanoSecond;


            System.out.println("packetSize_bits              : " + packetSize_bits);
            System.out.println("avgBitRate_bps               : " + avgBitRate_bps);
            System.out.println("timeBetweenPacketsSecond     : " + timeBetweenPacketsSecond);
            System.out.println("timeBetweenPacketsNanoSecond : " + timeBetweenPacketsNanoSecond);
            System.out.println("nanoSecPacketSpacing         : " + nanoSecPacketSpacing);

            // if(true){
            //     System.exit(1);
            // }

            this.packetSize = packetSize_byte;
            this.returnPort = returnPort;
            this.destAddr = destAddr;
            this.destPort = destPort;

            this.totalPackets = totalPackets;

            this.receiverRunnable = receiverRunnable;
        }

        
        public DatagramPacket buildPacket(InetAddress destAddr, int destPort, byte[] buff, int returnPort, int seqNum){
            System.arraycopy(toByteArray(returnPort),2,buff,0,2);
            System.arraycopy(toByteArray(seqNum),0,buff,2,4);
            return new DatagramPacket(buff, buff.length, destAddr, destPort);
        }



        public void run(){
        
            // create sender socket
            DatagramSocket socket = null;
            
            try{
                socket = new DatagramSocket();
            } catch (SocketException e){
                System.out.println("Socket error " + e.getMessage());
            }


            // pre-generate random byte array to be reused as packet payload
            byte[] buff = new byte[packetSize];
            new Random().nextBytes(buff);

            // initialize variables
            int currentPacketNum = 1;
            long nextPacketSendTime = 0L;

            System.out.println("Sending first packet.");
            DatagramPacket packet = buildPacket(destAddr, destPort, buff, returnPort, currentPacketNum);
            
            long firstPacketSendTime = 0;
            while(currentPacketNum < totalPackets){

                if(firstPacketSendTime == 0){
                    firstPacketSendTime = System.nanoTime();
                    packetData.put(currentPacketNum, firstPacketSendTime);
                }
                else{
                    packetData.put(currentPacketNum, System.nanoTime());
                }
                
                try{
                    socket.send(packet);
                    System.out.println("pack addr : " + packet.getAddress().getHostAddress() + " pack port : " + packet.getPort());
                }
                catch(IOException e){
                    System.out.println("caught socket io in SenderRunnable : " + e.getMessage());
                }

                if(currentPacketNum % 1000 == 0){
                    System.out.println("Sent " + currentPacketNum + " packets");
                }

                nextPacketSendTime = firstPacketSendTime + (nanoSecPacketSpacing*currentPacketNum);
                currentPacketNum++;
                packet = buildPacket(destAddr, destPort, buff, returnPort, currentPacketNum);

                long waitTimeNano = nextPacketSendTime - System.nanoTime();
                if(waitTimeNano > 0){
                    busyWaitNanos(waitTimeNano);
                }
            }
        }

    }




    


    // <seqNo, <sendTimeNano,receiveTimeNano>>
    public HashMap<Integer, Long> packetData;


    public Estimator(){
        packetData = new HashMap<Integer, Long>();
    }

    public void run(){

        int packetSize_byte = 1480;
        int returnPort = 4445;
        int avgBitRate_kbps = 100;

        InetAddress destAddr = null;
        try{
            destAddr = InetAddress.getByName("127.0.0.1");
        }
        catch(UnknownHostException e){
            System.out.println("UnknownHostException ");
        }
        
        int destPort = 4444;
        int totalPackets = 60000;

        ReceiverRunnable receiverRunnable = new ReceiverRunnable(packetData, returnPort, "output.txt");
        Thread receiverThread = new Thread(receiverRunnable);

        SenderRunnable senderRunnable = new SenderRunnable(packetData, packetSize_byte, returnPort, avgBitRate_kbps, destAddr, destPort, totalPackets);
        Thread senderThread = new Thread(senderRunnable);
        

        receiverThread.start();

        try{
            Thread.sleep(2000);
        } catch(InterruptedException e){
             System.out.println("InterruptedException ");
        }
        

        senderThread.start();
    }













}









    










