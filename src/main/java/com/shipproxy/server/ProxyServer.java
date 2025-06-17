package com.shipproxy.server;

import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ProxyServer {
    private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());
    
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public ProxyServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        LOGGER.info("Starting Offshore Proxy Server on port " + port);
        serverSocket = new ServerSocket(port);
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("Ship proxy connected from: " + clientSocket.getRemoteSocketAddress());
                
                handleShipConnection(clientSocket);
                
            } catch (IOException e) {
                if (running) {
                    LOGGER.log(Level.SEVERE, "Error accepting connection", e);
                }
            }
        }
    }

    private void handleShipConnection(Socket shipSocket) {
        try (DataInputStream shipIn = new DataInputStream(shipSocket.getInputStream());
             DataOutputStream shipOut = new DataOutputStream(shipSocket.getOutputStream())) {
            
            LOGGER.info("Handling persistent connection from ship");
            
            // Keep connection alive and process requests sequentially
            while (!shipSocket.isClosed() && running) {
                try {
                    int requestLength = shipIn.readInt();
                    
                    // Read HTTP request
                    byte[] requestBytes = new byte[requestLength];
                    shipIn.readFully(requestBytes);
                    String httpRequest = new String(requestBytes, "UTF-8");
                    
                    LOGGER.info("Received request: " + httpRequest.split("\r\n")[0]);
                    
                    byte[] response = processHttpRequest(httpRequest);
                    

                    shipOut.writeInt(response.length);
                    shipOut.write(response);
                    shipOut.flush();
                    
                    LOGGER.info("Response sent back to ship");
                    
                } catch (EOFException e) {
                    LOGGER.info("Ship disconnected");
                    break;
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error processing request", e);
                    break;
                }
            }
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error handling ship connection", e);
        } finally {
            try {
                shipSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing ship socket", e);
            }
        }
    }

    private byte[] processHttpRequest(String httpRequest) {
        try {
            // Parse the HTTP request
            String[] lines = httpRequest.split("\r\n");
            String requestLine = lines[0];
            String[] parts = requestLine.split(" ");
            
            if (parts.length < 3) {
                return createErrorResponse("400 Bad Request");
            }
            
            String method = parts[0];
            String url = parts[1];
            String version = parts[2];
            
            LOGGER.info("Processing: " + method + " " + url);
            
            if ("CONNECT".equalsIgnoreCase(method)) {
                return createConnectResponse();
            }
            
            URL targetUrl;
            try {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://" + url;
                }
                targetUrl = new URL(url);
            } catch (MalformedURLException e) {
                LOGGER.log(Level.WARNING, "Invalid URL: " + url, e);
                return createErrorResponse("400 Bad Request");
            }
            
            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    break;
                }
                
                if (line.contains(":")) {
                    String[] headerParts = line.split(":", 2);
                    String headerName = headerParts[0].trim();
                    String headerValue = headerParts[1].trim();
                    
                    if (!headerName.equalsIgnoreCase("host") &&
                        !headerName.equalsIgnoreCase("connection") &&
                        !headerName.equalsIgnoreCase("content-length")) {
                        connection.setRequestProperty(headerName, headerValue);
                    }
                }
            }
            
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                connection.setDoOutput(true);
                int bodyStart = httpRequest.indexOf("\r\n\r\n");
                if (bodyStart != -1) {
                    String body = httpRequest.substring(bodyStart + 4);
                    if (!body.isEmpty()) {
                        try (OutputStream os = connection.getOutputStream()) {
                            os.write(body.getBytes("UTF-8"));
                        }
                    }
                }
            }
            
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            
            StringBuilder response = new StringBuilder();
            response.append("HTTP/1.1 ").append(responseCode).append(" ").append(responseMessage).append("\r\n");
            
            for (String headerName : connection.getHeaderFields().keySet()) {
                if (headerName != null) {
                    for (String headerValue : connection.getHeaderFields().get(headerName)) {
                        response.append(headerName).append(": ").append(headerValue).append("\r\n");
                    }
                }
            }
            
            response.append("\r\n");
            
            InputStream inputStream;
            if (responseCode >= 400) {
                inputStream = connection.getErrorStream();
            } else {
                inputStream = connection.getInputStream();
            }
            
            if (inputStream != null) {
                ByteArrayOutputStream bodyOutput = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    bodyOutput.write(buffer, 0, bytesRead);
                }
                
                String responseBody = bodyOutput.toString("UTF-8");
                response.append(responseBody);
            }
            
            return response.toString().getBytes("UTF-8");
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing HTTP request", e);
            return createErrorResponse("502 Bad Gateway");
        }
    }

    private byte[] createErrorResponse(String error) {
        String response = "HTTP/1.1 " + error + "\r\n" +
                         "Content-Type: text/plain\r\n" +
                         "Content-Length: " + error.length() + "\r\n" +
                         "Connection: close\r\n\r\n" +
                         error;
        return response.getBytes();
    }

    private byte[] createConnectResponse() {
        String response = "HTTP/1.1 200 Connection Established\r\n\r\n";
        return response.getBytes();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error during shutdown", e);
        }
    }

    public static void main(String[] args) {
    
    	System.setProperty("user.timezone", "Asia/Kolkata");
        int port = 9090;
        
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }
        
        ProxyServer server = new ProxyServer(port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        
        try {
            server.start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start proxy server", e);
            System.exit(1);
        }
    }
}
