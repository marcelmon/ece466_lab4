
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

        public HashMap<Integer, SimpleEntry<Long,Long>> packetData;

        public ReceiverRunnable(HashMap<Integer, SimpleEntry<Long,Long>> packetData, int port, String outfile){
            
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

            int totalRecv = 0;
            while(true){


                try{
                    socket.receive(packet);
                    currentArrivalTimeNano = System.nanoTime();
                    totalRecv++;
                    System.out.println("TOTAL RECV : " + totalRecv);
                }
                catch(IOException e){
                    System.out.println("caught socket io in ReceiverRunnable : " + e.getMessage());
                    continue;
                }
                
                int seqNum = fromByteArray(packet.getData(), 2, 4);
                System.out.println("seq num : " + seqNum + " and if contains : " + packetData.containsKey(new Integer(seqNum)));


                if(firstSendTimeNano == 0L){

                    SimpleEntry<Long,Long> packdataEntry = packetData.get(new Integer(1));
                    firstSendTimeNano = packdataEntry.getKey();

                    System.out.println("first : " + 1 + " and if contains : " + packetData.containsKey(new Integer(1)));
                }
                
                
                

                SimpleEntry<Long, Long> sendReceiveTimes = packetData.get(new Integer(seqNum));


                long packetSendTimeNano = sendReceiveTimes.getKey();

                long packetSendTimeMicroNormal = (packetSendTimeNano - firstSendTimeNano)/1000;

                long packetReceiveTimeMicroNormal = (currentArrivalTimeNano - firstSendTimeNano)/1000;

                pout.println(seqNum + "\t"+ packetSendTimeMicroNormal + "\t" + packetReceiveTimeMicroNormal); 

                System.out.println("currentArrivalTimeNano " + currentArrivalTimeNano);
                SimpleEntry<Long, Long> sendReceiveTimesUpdated = new SimpleEntry<Long, Long>(packetSendTimeNano, currentArrivalTimeNano);
                packetData.put(seqNum, sendReceiveTimesUpdated);

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



        public void busyWaitNanos(long nanos){
            long waitUntil = System.nanoTime() + nanos;
            while(waitUntil > System.nanoTime()){
                ;
            }
        }


        public HashMap<Integer, SimpleEntry<Long,Long>> packetData;

        public SenderRunnable(HashMap<Integer, SimpleEntry<Long,Long>> packetData, int packetSize_byte, int returnPort, int avgBitRate_kbps, InetAddress destAddr, int destPort, int totalPackets){

            this.packetData = packetData;

            int packetSize_bits = packetSize_byte*8;
            int avgBitRate_bps = avgBitRate_kbps * 1000;
            double timeBetweenPacketsSecond = (double) packetSize_bits/(double) avgBitRate_bps;
            long timeBetweenPacketsNanoSecond = (long) (timeBetweenPacketsSecond * 1000000000);
            this.nanoSecPacketSpacing = timeBetweenPacketsNanoSecond;


            System.out.println("packetSize_bits              : " + packetSize_bits);
            System.out.println("avgBitRate_bps               : " + avgBitRate_bps);
            System.out.println("timeBetweenPacketsSecond     : " + timeBetweenPacketsSecond);
            System.out.println("timeBetweenPacketsNanoSecond : " + timeBetweenPacketsNanoSecond);
            System.out.println("nanoSecPacketSpacing         : " + this.nanoSecPacketSpacing);

            // if(true){
            //     System.exit(1);
            // }

            this.packetSize = packetSize_byte;
            this.returnPort = returnPort;
            this.destAddr = destAddr;
            this.destPort = destPort;

            this.totalPackets = totalPackets;

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
            long currentPacketSendTime = 0;

            while(currentPacketNum <= totalPackets){

                currentPacketSendTime = System.nanoTime();

                if(firstPacketSendTime == 0){
                    firstPacketSendTime = currentPacketSendTime;
                }
                
                try{
                    socket.send(packet);
                    packetData.put(new Integer(currentPacketNum), new SimpleEntry<Long, Long>(currentPacketSendTime, 99999999999999999L));
                    System.out.println("currentPacketNum " + currentPacketNum + " pack addr : " + packet.getAddress().getHostAddress() + " pack port : " + packet.getPort());
                }
                catch(IOException e){
                    System.out.println("caught socket io in SenderRunnable : " + e.getMessage());
                }

                // if(currentPacketNum % 1000 == 0){
                //     System.out.println("Sent " + currentPacketNum + " packets");
                // }

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
    public HashMap<Integer, SimpleEntry<Long,Long>> packetData;


    public Estimator(){
        
    }


    public HashMap<Integer, SimpleEntry<Long,Long>> runRunnables(
        int packetSize_byte, 
        int returnPort, 
        int avgBitRate_kbps,
        InetAddress destAddr,
        int destPort,
        int totalPackets
        ){


        HashMap<Integer, SimpleEntry<Long,Long>> packetData = new HashMap<Integer, SimpleEntry<Long,Long>>();

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

        try{
            senderThread.join();
            Thread.sleep(5000);
        }
        
        catch(InterruptedException e){

        }
        

        receiverThread.stop();


        return packetData;

    }




    public SimpleEntry<int[], Boolean> processPacketData(ArrayList<SimpleEntry<Integer,Integer>> maxBacklogsByRate, HashMap<Integer, SimpleEntry<Long,Long>> packetData, int totalPackets, int packetTrainTimeMillis, int packetSize_byte, int avgBitRate_kbps){


        Long firstSendTimeNano = packetData.get(1).getKey();

        List<Long> arrivalTimes = new ArrayList<Long>();
        List<Long> departureTimes = new ArrayList<Long>();

        for (int i = 1; i <= totalPackets; ++i) {
            if(!packetData.containsKey(i)){
                System.out.println("did not contain seq no. "+i);
                continue;
            }
            SimpleEntry<Long,Long> packetTimes = packetData.get(i);

            Long sendTime = packetTimes.getKey() - firstSendTimeNano;
            Long receiveTime = packetTimes.getValue() - firstSendTimeNano;


            System.out.println("send : " + sendTime + " received " + receiveTime );
            if(packetTimes.getValue() > 99999999999999997L){
                System.out.println(i + " packet was never received");
            }

            arrivalTimes.add(sendTime);
            departureTimes.add(receiveTime);
        }

        Collections.sort(arrivalTimes);
        Collections.sort(departureTimes);


        // create array for all microsecond between 

        int maxBacklog = 0;

        int[] backlogPerMillisecond = new int[packetTrainTimeMillis];

        // for each millisecond, find total arrivals and total departures occuring before this time
        // use these sums to determine backlog of packets
        for (int i = 0; i < backlogPerMillisecond.length; ++i) {

            // accumulators in bytes
            int totalArrialsBytes = 0;
            int totalDeparturesBytes = 0;

            for (Long arrivalTimeNano : arrivalTimes) {
                if(arrivalTimeNano < i * 1000000){
                    totalArrialsBytes += packetSize_byte;
                }
            }

            for (Long departureTimeNano : departureTimes) {
                if(departureTimeNano < i * 1000000){
                    totalDeparturesBytes += packetSize_byte;
                }
            }


            int backlog = totalArrialsBytes - totalDeparturesBytes;

            if(backlog > maxBacklog){
                maxBacklog = backlog;
            }
        }


        maxBacklogsByRate.add(new SimpleEntry<Integer,Integer>(avgBitRate_kbps, maxBacklog));


        int[] serviceCurveByMillis = new int[packetTrainTimeMillis];
        for (int i = 0; i < serviceCurveByMillis.length; ++i) {
            serviceCurveByMillis[i] = 0;
        }



        boolean hasImproved = false;

        for (int i = 0; i < serviceCurveByMillis.length; ++i) {
            
            for (SimpleEntry<Integer,Integer> rateBacklog: maxBacklogsByRate) {
                
                int serviceCurveValue = rateBacklog.getKey()*i - (8*rateBacklog.getValue());

                if(serviceCurveValue < 0){
                    continue;
                    // serviceCurveValue = 0;
                }

                if(serviceCurveValue > serviceCurveByMillis[i]){
                    serviceCurveByMillis[i] = serviceCurveValue;

                    // System.out.println("serviceCurveValue " + serviceCurveValue + " , " + serviceCurveByMillis[i] + )
                    hasImproved = true;
                }
            }
        }

        SimpleEntry<int[], Boolean> ret = new SimpleEntry<int[], Boolean>(serviceCurveByMillis, hasImproved);
        return ret;



    }

    public void run(){

        // <rate, maxBacklog>
        ArrayList<SimpleEntry<Integer,Integer>> maxBacklogsByRate = new ArrayList<SimpleEntry<Integer,Integer>>();

        int packetSize_byte = 1000;
        int returnPort = 4445;

        InetAddress destAddr = null;
        try{
            destAddr = InetAddress.getByName("127.0.0.1");
        }
        catch(UnknownHostException e){
            System.out.println("UnknownHostException ");
        }
        

        

        int destPort = 4444;
        // int totalPackets = 60000;



        int avgBitRate_kbps = 100;

        int currentIndex = 0;




        // total amount of milliseconds to include (calculate the service curve in millisecond intervals)
        int packetTrainTimeMillis = 2000;

        double totalSeconds = (double) packetTrainTimeMillis / (double) 1000;




        int[] serviceCurveByMillis = new int[packetTrainTimeMillis];
        for (int i = 0; i < serviceCurveByMillis.length; ++i) {
            serviceCurveByMillis[i] = 0;
        }




        int bitsPerPacket = 8*packetSize_byte;



        while(true){


            int bitsPerSecond = avgBitRate_kbps*1000;

            double packetsPerSecond = (double) bitsPerSecond / (double) bitsPerPacket;

            int totalPackets = (new Double(packetsPerSecond*totalSeconds)).intValue();

            HashMap<Integer, SimpleEntry<Long,Long>> packetData = runRunnables(packetSize_byte, returnPort, avgBitRate_kbps, destAddr, destPort, totalPackets);
        
            SimpleEntry<int[], Boolean> ret = processPacketData(maxBacklogsByRate, packetData, totalPackets, packetTrainTimeMillis, packetSize_byte, avgBitRate_kbps);
            
            ArrayList<Integer> betterI = new ArrayList<Integer>();

            boolean stillGreater = false;
            int totalGreater = 0;
            if(currentIndex == 0){
                serviceCurveByMillis = ret.getKey();
            }

            else{
                for (int i = 0; i < serviceCurveByMillis.length; ++i) {
                    if(ret.getKey()[i] > serviceCurveByMillis[i]){
                        // still greater
                        stillGreater = true;
                        betterI.add(i);
                        totalGreater++;
                    }
                }
                if(stillGreater == false){
                     System.out.println("DONE!");
                    System.exit(1);
                }
                else{
                   serviceCurveByMillis = ret.getKey(); 
                   System.out.println("totalGreater " + totalGreater);
                }
                // for(Integer i : betterI){
                //     System.out.print(" "+i);
                // }
            }

            // if(count > 0 && ret.getValue() == false){
               
            // }
            currentIndex++;

            avgBitRate_kbps+=50;

            returnPort++;
        }

        

            
        // // <sequence number, <send time, receive time>>
        // packetData = new HashMap<Integer, SimpleEntry<Long,Long>>();




        // ReceiverRunnable receiverRunnable = new ReceiverRunnable(packetData, returnPort, "output.txt");
        // Thread receiverThread = new Thread(receiverRunnable);

        // SenderRunnable senderRunnable = new SenderRunnable(packetData, packetSize_byte, returnPort, avgBitRate_kbps, destAddr, destPort, totalPackets);
        // Thread senderThread = new Thread(senderRunnable);
        

        // receiverThread.start();

        // try{
        //     Thread.sleep(1000);
        // } catch(InterruptedException e){
        //      System.out.println("InterruptedException ");
        // }
        

        // senderThread.start();

        // senderThread.join();

        // Thread.sleep(2000);

        // receiverThread.stop();


        // List<Long> arrivalTimes = new ArrayList<Long>();
        // List<Long> departureTimes = new ArrayList<Long>();

        // for (int i = 1; i <= totalPackets; ++i) {
        //     SimpleEntry<Long,Long> packetTimes = packetData.get(i);

        //     Long sendTime = packetTimes.getKey();
        //     Long receiveTime = packetTimes.getValue();

        //     if(receiveTime > 10161519016191){
        //         // packet was never received
        //     }

        //     arrivalTimes.add(sendTime);
        //     departureTimes.add(receiveTime);
        // }

        // arrivalTimes.sort();
        // departureTimes.sort();


        // // create array for all microsecond between 

        // int maxBacklog = 0;

        // int[] backlogPerMillisecond = int[packetTrainTimeMillis + 1000];
        // for (int i = 0; i < backlogPerMillisecond.length; ++i) {
        //     int totalArrialsBytes = 0;
        //     int totalDeparturesBytes = 0;

        //     for (Long arrivalTimeNano : arrivalTimes) {
        //         if(arrivalTimeNano > i * 1000000){
        //             break;
        //         }
        //         totalArrialsBytes += packetSize_byte;
        //     }


        //     for (Long departureTimeNano : departureTimes) {
        //         if(departureTimeNano > i * 1000000){
        //             break;
        //         }
        //         totalDeparturesBytes += packetSize_byte;
        //     }


        //     int backlog = totalDeparturesBytes - totalArrialsBytes;

        //     if(backlog > maxBacklog){
        //         maxBacklog = backlog;
        //     }
        // }


        // maxBacklogsByRate.add(new SimpleEntry<int,int>(avgBitRate_kbps, maxBacklog));


        // int[] serviceCurveByMillis = int[packetTrainTimeMillis + 1000];
        // for (int i = 0; i < serviceCurveByMillis.length; ++i) {
        //     serviceCurveByMillis[i] = 0;
        // }



        // boolean hasImproved = false;

        // for (int i = 0; i < serviceCurveByMillis.length; ++i) {
            
        //     for (SimpleEntry<int,int> rateBacklog: maxBacklogsByRate) {
                
        //         int serviceCurveValue = rateBacklog.getKey()*i - rateBacklog.getValue();

        //         if(serviceCurveValue < 0){
        //             continue;
        //             // serviceCurveValue = 0;
        //         }

        //         if(serviceCurveValue > serviceCurveByMillis[i]){
        //             serviceCurveByMillis[i] = serviceCurveValue;
        //             hasImproved = true;
        //         }
        //     }
        // }


        // if(hasImproved == false){
        //     // DONE
        // }




    }













}









    










