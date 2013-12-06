package slo.helper.app;

// HTTPExtensionExample.java
// Copyright (c) MIT Media Laboratory, 2013
//
// This example Scratch helper app runs a tiny HTTP server that allows Scratch to
// play a beep sound and set and get the value of a variable named 'volume'.
//
// Inspired by Tom Lauwers Finch/Hummingbird server and Conner Hudson's Snap extensions.
// I changed it up -- Matthew Vaughan - Nov 2013 - UMass Lowell
import java.io.*;
import java.net.*;
import java.util.*;

public class SLOHelperApp extends Thread {

    private static int SERVERPORT = 40111; // the port of the server
    private static String SADDR = "127.0.0.1"; // server address
    private static final int PORT = 12345; // set to your extension's port number
    
    public static double orchbeat = 0; // number of beats so far in current block
    public static int consecutivePolls = 0; // number of consecutive polls
    
    private static PrintWriter sSockOut;
    private static BufferedReader sSockIn;
    
    private static InputStream sockIn;
    private static OutputStream sockOut;
    
    private static boolean connected = false;
    private javax.swing.JLabel isConnectedDisplay = null;
    
    public static SLOHelperApp connect = new SLOHelperApp();

    private SLOHelperApp() {
    }

    public static SLOHelperApp getInstance() {
        return connect;
    }

    public int getSeverport() {
        return SERVERPORT;
    }

    public void setServerport(int serverport) {
        SERVERPORT = serverport;
    }

    public String getSADDR() {
        return SADDR;
    }

    public void setSADDR(String serverAddress) {
        SADDR = serverAddress;
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public void setConnected(boolean con) {
        connected = con;
        if (isConnectedDisplay != null) {
            if(con) {
                isConnectedDisplay.setText("Connected!");
            } else {
                isConnectedDisplay.setText("Not Connected");
            }
        }
    }

    public void getServerConnection() {
        (new SLOHelperApp().getInstance()).start();
    }

    public void run() {
        
        System.out.println("Now in connect thread!");
        /*		
         if ( args.length > 0 ) {
         SADDR = args[0];
         }
         if ( args.length > 1 ) {
         SERVERPORT = new Integer( args[1] ).intValue();
         }      
         */
        InetAddress addr = null;
        Socket sSock = null; // the server (which we will be a client of)

        while (!isConnected()) {
            try {
                sSock = new Socket(SADDR, SERVERPORT);
                setConnected(true);
            } catch (Exception e) {
                System.out.println("Connecting");
            //  setConnected(false);
            }
        }

        try {
            sSockOut = new PrintWriter(sSock.getOutputStream(), true);
        } catch (IOException exception) {
            throw new RuntimeException("Unable to print to output stream", exception);
        }
        
        try {
           sSockIn = new BufferedReader(new InputStreamReader(sSock.getInputStream()));
        } catch (IOException exception) {
           throw new RuntimeException("Unable to get inputstream", exception);
        }

        // anything that gets sent, like so, will get executed by the server...
        // sSockOut.println("(define x 40404)");

        try {
            addr = InetAddress.getLocalHost();
        } catch (IOException exception) {
            throw new RuntimeException("Unable to get local host", exception);
        }

        setConnected(true);
        System.out.println("HTTPExtensionExample helper app started on " + addr.toString());

        ServerSocket serverSock = null;

        try {
            serverSock = new ServerSocket(PORT);
        } catch (IOException exception) {
            throw new RuntimeException("Unable to get port", exception);
        }
        while (true) {
            Socket sock = null;
            try {
                sock = serverSock.accept();
                sockIn = sock.getInputStream();
                sockOut = sock.getOutputStream();
            } catch (IOException exception) {
                throw new RuntimeException("Unable to open socket", exception);
            }
            try {
                handleRequest();
            } catch (Exception e) {
                e.printStackTrace(System.err);
                sendResponse("unknown server error");
            }
            try {
                sock.close();
            } catch (IOException exception) {
                throw new RuntimeException("Unable to close socket", exception);
            }
        }
    }

    private static void handleRequest() throws IOException {
        String httpBuf = "";
        int i;

        // read data until the first HTTP header line is complete (i.e. a '\n' is seen)
        while ((i = httpBuf.indexOf('\n')) < 0) {
            byte[] buf = new byte[5000];
            int bytes_read = sockIn.read(buf, 0, buf.length);
            if (bytes_read < 0) {
                System.out.println("Socket closed; no HTTP header.");
                return;
            }
            httpBuf += new String(Arrays.copyOf(buf, bytes_read));
        }

        String header = httpBuf.substring(0, i);
        if (header.indexOf("GET ") != 0) {
            System.out.println("This server only handles HTTP GET requests.");
            return;
        }
        i = header.indexOf("HTTP/1");
        if (i < 0) {
            System.out.println("Bad HTTP GET header.");
            return;
        }
        header = header.substring(5, i - 1);
        if (header.equals("favicon.ico")) {
            return; // igore browser favicon.ico requests
        } else if (header.equals("crossdomain.xml")) {
            sendPolicyFile();
        } else if (header.length() == 0) {
            doHelp();
        } else {
            doCommand(header);
        }
    }

    private static void sendPolicyFile() {
        // Send a Flash null-teriminated cross-domain policy file.
        String policyFile =
                "<cross-domain-policy>\n"
                + "  <allow-access-from domain=\"*\" to-ports=\"" + PORT + "\"/>\n"
                + "</cross-domain-policy>\n\0";
        sendResponse(policyFile);
    }

    private static void sendResponse(String s) {
        String crlf = "\r\n";
        String httpResponse = "HTTP/1.1 200 OK" + crlf;
        httpResponse += "Content-Type: text/html; charset=ISO-8859-1" + crlf;
        httpResponse += "Access-Control-Allow-Origin: *" + crlf;
        httpResponse += crlf;
        httpResponse += s + crlf;
        try {
            byte[] outBuf = httpResponse.getBytes();
            sockOut.write(outBuf, 0, outBuf.length);
        } catch (Exception ignored) {
        }
    }

    private static void doCommand(String cmdAndArgs) {

        System.out.println("Received: " + cmdAndArgs);

        // Essential: handle commands understood by this server
        String response = "okay";
        String[] parts = cmdAndArgs.split("/");
        String cmd = parts[0];
        if (cmd.equals("playNote")) {
            int noteNum = Integer.parseInt(parts[1]);
            double duration = Double.parseDouble(parts[2]);
            consecutivePolls = 0; // this is not a poll!

            String lispExpr = new String("(noteOn (+ (nm) (* (beat) " + orchbeat + " )) (myChannel) " + noteNum + " 127)(noteOff (+ (nm) (* (beat) " + (orchbeat + duration) + ")) (myChannel) " + noteNum + " 127)");
            sSockOut.println(lispExpr);
            sSockOut.flush();

            orchbeat += duration; // so were know where the next beat starts
        } else if (cmd.equals("changeInstrument")) {
            int instrumentNum = getInstrumentNumber(parts[1]);
            consecutivePolls = 0; // this is not a poll!

            String lispExpr = new String("(changeInst " + instrumentNum + ")");
            sSockOut.println(lispExpr);
            sSockOut.flush();
        } else if (cmd.equals("drumMode")) {
            String lispExpr = new String("(drumMode #t)");
            sSockOut.println(lispExpr);
            sSockOut.flush();
        } else if (cmd.equals("instMode")) {
            String lispExpr = new String("(drumMode #f)");
            sSockOut.println(lispExpr);
            sSockOut.flush();
        } else if (cmd.equals("poll")) {
            consecutivePolls++;
            if (consecutivePolls >= 4) {
                consecutivePolls = 0;
                orchbeat = 0;
            }
        } else if (cmd.equals("rest")) {
            System.out.println("GOT A REST");
            int noteNum = 0;
            double duration = Double.parseDouble(parts[1]);
            consecutivePolls = 0; // this is not a poll!

            String lispExpr = new String("(noteOn (+ (nm) (* (beat) " + orchbeat + " )) (myChannel) " + noteNum + " 127)(noteOff (+ (nm) (* (beat) " + (orchbeat + duration) + ")) (myChannel) " + noteNum + " 127)");
            sSockOut.println(lispExpr);
            sSockOut.flush();

            orchbeat += duration; // so were know where the next beat starts
        } else {
            response = "unknown command: " + cmd;
        }
        sendResponse(response);
    }

    private static int getInstrumentNumber(String instrument) {
        if (instrument.equalsIgnoreCase("Piano")) {
            return 1;
        }
        if (instrument.equalsIgnoreCase("Marimba")) {
            return 12;
        }
        if (instrument.equalsIgnoreCase("Acst Gtr")) {
            return 24;
        }
        if (instrument.equalsIgnoreCase("Dist Gtr")) {
            return 30;
        }
        if (instrument.equalsIgnoreCase("Bass")) {
            return 33;
        }
        if (instrument.equalsIgnoreCase("Strings")) {
            return 48;
        }
        if (instrument.equalsIgnoreCase("Voice")) {
            return 91;
        } else {
            return 1; // default to piano
        }
    }

    private static void doHelp() {
        // Optional: return a list of commands understood by this server
        String help = "HTTP Extension Example Server<br><br>";
        help += "playBeep - play the system beep<br>";
        help += "poll - return all sensor values, one sensor per line, with the sensor name and value separated by a space<br>";
        help += "setVolume/[num] - set the volume to the given number (0-10)<br>";
        help += "volume - return the current volume<br>";
        sendResponse(help);
    }
    
    public void setDisplays(javax.swing.JLabel cDisplay) {
        isConnectedDisplay = cDisplay;
    }
}

