package com.shipproxy.client;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ProxyClient {
    private static final Logger LOGGER = Logger.getLogger(ProxyClient.class.getName());
    
    private final int localPort;
    private final String serverHost;
    private final int serverPort;
    private final ExecutorService requestQueue;
    private ServerSocket serverSocket;
    private Socket serverConnection;
    private DataOutputStream serverOut;
    private DataInputStream serverIn;
    private AtomicBoolean isConnectedToServer = new AtomicBoolean(false);
    private volatile boolean running = true;

    public ProxyClient(int localPort, String serverHost, int serverPort) {
        this.localPort = localPort;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.requestQueue = Executors.newSingleThreadExecutor();
    }

    public void start() throws IOException {
        LOGGER.info("Starting Ship Proxy Client on port " + localPort);
        
        connectToServer();
        
        serverSocket = new ServerSocket(localPort);
        LOGGER.info("Ship Proxy listening on port " + localPort);
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleClientConnection(clientSocket);
            } catch (IOException e) {
                if (running) {
                    LOGGER.log(Level.SEVERE, "Error accepting client connection", e);
                }
            }
        }
    }

    private void connectToServer() throws IOException {
        LOGGER.info("Connecting to offshore server at " + serverHost + ":" + serverPort);
        serverConnection = new Socket(serverHost, serverPort);
        serverOut = new DataOutputStream(serverConnection.getOutputStream());
        serverIn = new DataInputStream(serverConnection.getInputStream());
        isConnectedToServer.set(true);
        LOGGER.info("Connected to offshore server");
    }

    private void handleClientConnection(Socket clientSocket) {
        requestQueue.submit(() -> {
            try (BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream clientOut = clientSocket.getOutputStream()) {
                
                processHttpRequest(clientIn, clientOut);
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling client request", e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing client socket", e);
                }
            }
        });
    }

    private void processHttpRequest(BufferedReader clientIn, OutputStream clientOut) throws IOException {
        StringBuilder requestBuilder = new StringBuilder();
        String line;
        int contentLength = 0;
        boolean hasContentLength = false;

        while ((line = clientIn.readLine()) != null) {
            requestBuilder.append(line).append("\r\n");
            
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
                hasContentLength = true;
            }
            
            if (line.isEmpty()) {
                break;
            }
        }

        if (hasContentLength && contentLength > 0) {
            char[] body = new char[contentLength];
            clientIn.read(body, 0, contentLength);
            requestBuilder.append(new String(body));
        }

        String httpRequest = requestBuilder.toString();
        
        if (httpRequest.trim().isEmpty()) {
            return;
        }

        LOGGER.info("Processing HTTP request: " + httpRequest.split("\r\n")[0]);

        synchronized (this) {
            if (!isConnectedToServer.get()) {
                try {
                    connectToServer();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to reconnect to server", e);
                    sendErrorResponse(clientOut, "502 Bad Gateway");
                    return;
                }
            }

            try {
                byte[] requestBytes = httpRequest.getBytes("UTF-8");
                serverOut.writeInt(requestBytes.length);
                serverOut.write(requestBytes);
                serverOut.flush();

                int responseLength = serverIn.readInt();
                byte[] responseBytes = new byte[responseLength];
                serverIn.readFully(responseBytes);
                
                clientOut.write(responseBytes);
                clientOut.flush();
                
                LOGGER.info("Request processed successfully");
                
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error communicating with server", e);
                isConnectedToServer.set(false);
                try {
                    serverConnection.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Error:", ex);
                }
                sendErrorResponse(clientOut, "502 Bad Gateway");
            }
        }
    }

    private void sendErrorResponse(OutputStream clientOut, String error) {
        try {
            String response = "HTTP/1.1 " + error + "\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: " + error.length() + "\r\n" +
                            "Connection: close\r\n\r\n" +
                            error;
            clientOut.write(response.getBytes());
            clientOut.flush();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error sending error response", e);
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (serverConnection != null && !serverConnection.isClosed()) {
                serverConnection.close();
            }
            requestQueue.shutdown();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error during shutdown", e);
        }
    }

    public static void main(String[] args) {
        int localPort = 8080;
        System.setProperty("user.timezone", "Asia/Kolkata");
        String serverHost = "localhost";
        int serverPort = 9090;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--local-port":
                    if (i + 1 < args.length) {
                        localPort = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--server-host":
                    if (i + 1 < args.length) {
                        serverHost = args[++i];
                    }
                    break;
                case "--server-port":
                    if (i + 1 < args.length) {
                        serverPort = Integer.parseInt(args[++i]);
                    }
                    break;
            }
        }

        ProxyClient client = new ProxyClient(localPort, serverHost, serverPort);
        
        Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
        
        try {
            client.start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start proxy client", e);
            System.exit(1);
        }
    }
}
