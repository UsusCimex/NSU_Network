package ru.nsu;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileTransferServer {
    private static final String UPLOAD_DIR = "uploads/";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java FileTransferServer \"port\"");
            return;
        }
        int port = Integer.parseInt(args[0]);
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        File uploadDir = new File("uploads");
        if (!uploadDir.exists()) {
            uploadDir.mkdir(); // Создаем директорию, если она не существует.
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                short fileNameLength = in.readShort();
                byte[] fileNameBytes = new byte[fileNameLength];
                in.readFully(fileNameBytes);
                String fileName = new String(fileNameBytes);
                int fileSize = in.readInt();

                System.out.println("Start receive : " + fileName + "(" + fileSize / 1024 / 1024 + " Mb)");
                File outputFile = new File(UPLOAD_DIR + fileName);
                FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                byte[] buffer = new byte[8192];
                long startTime = System.currentTimeMillis();
                long tempTime = startTime;
                long totalBytesReceived = 0;
                long tempBytesReceived = 0;

                while (totalBytesReceived < fileSize) {
                    int bytesRead = in.read(buffer);
                    if (bytesRead == -1) {
                        break;
                    }
                    fileOutputStream.write(buffer, 0, bytesRead);
                    totalBytesReceived += bytesRead;
                    tempBytesReceived += bytesRead;
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - tempTime >= 3000) { //3 second
                        double instantSpeed = tempBytesReceived / ((currentTime - tempTime) / 1000.0);
                        double avgSpeed = totalBytesReceived / ((currentTime - startTime) / 1000.0);
                        System.out.println("Instant Speed: " + instantSpeed / 1024 / 1024 + " Mb/s");
                        System.out.println("Average Speed: " + avgSpeed / 1024 / 1024 + " Mb/s");
                        tempTime = currentTime;
                        tempBytesReceived = 0;
                    }
                }

                fileOutputStream.close();
                System.out.println("File " + fileName + " received");
                if (totalBytesReceived == fileSize) {
                    out.write("SUC".getBytes());
                } else {
                    out.write("ERR".getBytes());
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
