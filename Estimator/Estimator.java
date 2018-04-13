/*


The entry point for the estimator is Estimator::run() on line 502. Please see the comments above the main while loop at line 546.


Two other classes are included in this file, SenderRunnable and ReceiverRunnable action as traffic source and sink.


*/
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




    // the receiver runnable will record packet receive times (in nano second) to the packetData hash map
    // it also prints out the send and receive times to file when a packet arrives
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
            while(true){


                try{
                    socket.receive(packet);
                }
                catch(IOException e){
                    System.out.println("caught socket io in ReceiverRunnable : " + e.getMessage());
                }
                
                currentArrivalTimeNano = System.nanoTime();

                if(firstSendTimeNano == 0L){
                    firstSendTimeNano = packetData.get(1).getKey();
                }
                
                int seqNum = fromByteArray(packet.getData(), 2, 4);


                SimpleEntry<Long, Long> sendReceiveTimes = packetData.get(seqNum);


                long packetSendTimeNano = sendReceiveTimes.getKey();

                long packetSendTimeMicroNormal = (packetSendTimeNano - firstSendTimeNano)/1000;

                long packetReceiveTimeMicroNormal = (currentArrivalTimeNano - firstSendTimeNano)/1000;

                pout.println(seqNum + "\t"+ packetSendTimeMicroNormal + "\t" + packetReceiveTimeMicroNormal); 


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










    // the sender runnable class will take as parameter the packetsize, dest info, return port to use, avg bit rate, and total packets to send
    // it will record the send times indexed by sequence number in the packetData object

    public class SenderRunnable implements Runnable{



        public int totalPackets;

        public long nanoSecPacketSpacing;

        public int packetSize;

        public int returnPort;

        public InetAddress destAddr;

        public int destPort;


        public ReceiverRunnable receiverRunnable;


        // spin-wait used instead of thread.sleep for better time granularity
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

            // will send a packet until the totalPackets is reached
            while(currentPacketNum <= totalPackets){

                if(firstPacketSendTime == 0){
                    firstPacketSendTime = System.nanoTime();           

                    //     packetData will contain the send and receive times indexed by seq id                          
                    packetData.put(currentPacketNum, new SimpleEntry<Long, Long>(firstPacketSendTime, 99999999999999999L));
                }
                else{
                    packetData.put(currentPacketNum,  new SimpleEntry<Long, Long>(System.nanoTime(),99999999999999999L));
                }
                
                try{
                    socket.send(packet);
                    // System.out.println("pack addr : " + packet.getAddress().getHostAddress() + " pack port : " + packet.getPort());
                }
                catch(IOException e){
                    System.out.println("caught socket io in SenderRunnable : " + e.getMessage());
                }

                if(currentPacketNum % 1000 == 0){
                    System.out.println("Sent " + currentPacketNum + " packets");
                }

                nextPacketSendTime = firstPacketSendTime + (nanoSecPacketSpacing*currentPacketNum);
                currentPacketNum++;

                // build packet used to create a packet containing the seq number and return port
                packet = buildPacket(destAddr, destPort, buff, returnPort, currentPacketNum);

                long waitTimeNano = nextPacketSendTime - System.nanoTime();

                // wait until packet is ready to send
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


    // this function will run the sender and receiver and return the send and receive times of each packet
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
            Thread.sleep(1000);
        } catch(InterruptedException e){
             System.out.println("InterruptedException ");
        }
        

        senderThread.start();

        try{
            // ensure that all packets are sent
            senderThread.join();

            // give some time to ensure there are no packets remaining in the blackbox buffer
            Thread.sleep(5000);
        }
        
        catch(InterruptedException e){

        }
        
        // force the receive to stop
        receiverThread.stop();


        return packetData;

    }


    // process packet data takes as arguments the packetData object containing all send and receive times for each packet, as well as the previously recorded max backlogs
    // it is responsible for calulating the new max backlog for this iteration (using packetData)
    //      and for each milliscond of the packet train time, it will find the max(rt- Bmax) val used in service curve estimation
    public SimpleEntry<int[], Boolean> processPacketData(ArrayList<SimpleEntry<Integer,Integer>> maxBacklogsByRate, HashMap<Integer, SimpleEntry<Long,Long>> packetData, int totalPackets, int packetTrainTimeMillis, int packetSize_byte, int avgBitRate_kbps){


        Long firstSendTimeNano = packetData.get(1).getKey();

        List<Long> arrivalTimes = new ArrayList<Long>();
        List<Long> departureTimes = new ArrayList<Long>();


        // the sendTime (arrival time) and receive times (departure times) of each packet are extracted from the packetData object
        for (int i = 1; i <= totalPackets; ++i) {
            if(!packetData.containsKey(i)){
                System.out.println("did not contain seq no. "+i);
                continue;
            }
            SimpleEntry<Long,Long> packetTimes = packetData.get(i);

            Long sendTime = packetTimes.getKey() - firstSendTimeNano;
            Long receiveTime = packetTimes.getValue() - firstSendTimeNano;

            if(packetTimes.getValue() > 99999999999999997L){
                // packet was never received
            }

            arrivalTimes.add(sendTime);
            departureTimes.add(receiveTime);
        }

        Collections.sort(arrivalTimes);
        Collections.sort(departureTimes);


        // create array for all microsecond between 

        int maxBacklog = 0;


        
        // and for each millisecond the backlog is calculated by summing all arrivals and subtracting from that the sum of all departures
        // then the max backlog from the entire packet train is determined and writen to maxBacklog
        int[] backlogPerMillisecond = new int[packetTrainTimeMillis];
        for (int i = 0; i < backlogPerMillisecond.length; ++i) {
            int totalArrialsBits = 0;
            int totalDeparturesBits = 0;

            for (Long arrivalTimeNano : arrivalTimes) {
                if(arrivalTimeNano < i * 1000000){
                    totalArrialsBits += packetSize_byte*8;
                }
                
            }


            for (Long departureTimeNano : departureTimes) {
                if(departureTimeNano < i * 1000000){
                    totalDeparturesBits += packetSize_byte*8;
                }
                
            }


            int backlog = totalArrialsBits - totalDeparturesBits;

            if(backlog > maxBacklog){
                maxBacklog = backlog;
            }
        }

        maxBacklogsByRate.add(new SimpleEntry<Integer,Integer>(avgBitRate_kbps, maxBacklog));


        // a new service curve for this iteration is initialized
        int[] serviceCurveByMillis = new int[packetTrainTimeMillis];
        for (int i = 0; i < serviceCurveByMillis.length; ++i) {
            serviceCurveByMillis[i] = 0;
        }


        // no longer used
        boolean hasImproved = false;




        for (int i = 0; i < serviceCurveByMillis.length; ++i) {
            
            // iterate through each previously recorded rate->max backlog and calculate the service curve at time i (ms) use rt- Bmax
            for (SimpleEntry<Integer,Integer> rateBacklog: maxBacklogsByRate) {
                
                int serviceCurveValue = rateBacklog.getKey()*i - rateBacklog.getValue();

                if(serviceCurveValue < 0){
                    continue;
                    // serviceCurveValue = 0;
                }

                // the service curve value at this milliscond is added only if it is an improvement
                if(serviceCurveValue > serviceCurveByMillis[i]){
                    serviceCurveByMillis[i] = serviceCurveValue;
                    hasImproved = true;
                }
            }
        }


        // return serviceCurveByMillis, hasImproved is no longer used as this is determined outside this function
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
        


        int packetTrainTimeMillis = 2000;


        double totalSeconds = (double) packetTrainTimeMillis / (double) 1000;


        int bitsPerPacket = 8*packetSize_byte;


        int destPort = 4444;



        int avgBitRate_kbps = 700;

        int count = 0;


        int[] serviceCurveByMillis = new int[packetTrainTimeMillis];
        for (int i = 0; i < serviceCurveByMillis.length; ++i) {
            serviceCurveByMillis[i] = 0;
        }

        int currentIndex = 0 ;


        // this is the outside loop for each iteration of the esimator
        // the first rate selected is that which is set in avgBitRate_kbps above
        // each iteration will start by calling runRunnables() 
        //      which will start a send and receive thread, and send a packet train the the blackbox
        //      and keep a record of the send and receive times, returning the hashmap that stores these values
        // next the processPacketData() function is called to build the service curve estimate for this iteration
        // the final step in each loop iteration is to compare the new estimate returned from processPacketData() 
        //      against the previous service cureve stored in serviceCurveByMillis
        // if there is any imprvoment then the serviceCurveByMillis is set to the new estimate
        while(true){


            int bitsPerSecond = avgBitRate_kbps*1000;

            double packetsPerSecond = (double) bitsPerSecond / (double) bitsPerPacket;

            int totalPackets = (new Double(packetsPerSecond*totalSeconds)).intValue();

            System.out.println("Current kbps : " + avgBitRate_kbps);


            // start the send and receive threads and return a hashmap containing : <seq num ,  <send time nano, receive time nano>>
            HashMap<Integer, SimpleEntry<Long,Long>> packetData = runRunnables(packetSize_byte, returnPort, avgBitRate_kbps, destAddr, destPort, totalPackets);
        

            // build a new service curve estimate for the particular estimator rate used in this iteration
            // will also store the calculated max backlog for this rate in maxBacklogsByRate
            // note : the boolean return is not used
            SimpleEntry<int[], Boolean> ret = processPacketData(maxBacklogsByRate, packetData, totalPackets, packetTrainTimeMillis, packetSize_byte, avgBitRate_kbps);
            ArrayList<Integer> betterI = new ArrayList<Integer>();

            boolean stillGreater = false;
            int totalGreater = 0;

            // is the first iteration, thus there is no previous estimate to compare, set the current service curve estimate
            if(currentIndex == 0){
                serviceCurveByMillis = ret.getKey();
            }

            else{

                // iterate through each millisecond of the previous service curve estimate and compare it against the estimate used in this iteration
                for (int i = 0; i < serviceCurveByMillis.length; ++i) {
                    if(ret.getKey()[i] > serviceCurveByMillis[i]){
                        // still greater
                        stillGreater = true;
                        betterI.add(i);
                        totalGreater++;
                    }
                }

                // if none of the values of the new service curve estimate were better than the previous, we are done
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

            try{
                // output the value for the current service curve estimate to file (do not perform this step if the estimate wasn't better)
                FileOutputStream fout =  new FileOutputStream("serviceCurveValues_max_test_rate_"+avgBitRate_kbps+"_kbps.txt");
                PrintStream pout = new PrintStream (fout);
                for (int i = 0; i < serviceCurveByMillis.length; ++i) {
                    pout.println(i + "\t" + serviceCurveByMillis[i]);
                }
            }
            catch(IOException e){
                System.out.println("caught io in ReceiverRunnable : " + e.getMessage());
            }

            
            currentIndex++;


            // increment the estimator rate
            avgBitRate_kbps+=100;

            returnPort++;
        }

        

            




    }













}









    










