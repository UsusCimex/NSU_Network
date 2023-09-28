package ru.nsu;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.nsu.FileWorker.getFileExtension;
import static ru.nsu.FileWorker.removeFileExtension;

public class FileTransferServer {
    private static final String UPLOAD_DIR = "uploads/";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(3);

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java FileTransferServer \"port\"");
            return;
        }
        int port = Integer.parseInt(args[0]);

        File uploadDir = new File("uploads");
        if (!uploadDir.exists()) {
            if (!uploadDir.mkdir()) { // Создаем директорию, если она не существует.
                System.err.println("Failed to create upload directory: " + uploadDir.getAbsolutePath());
                return;
            }
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("An error occurred while setting up the server or accepting client connections");
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
                String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                String filePath = UPLOAD_DIR + fileName;
                long fileSize = in.readLong();
                File outputFile = new File(filePath);

                int index = 1;
                while (outputFile.exists()) {
                    String fileExtension = getFileExtension(filePath);
                    String mainFileName = removeFileExtension(filePath);
                    String newFileName = mainFileName + index + "." + fileExtension;
                    outputFile = new File(newFileName);
                    index++;
                }

                System.out.println("Start receive : " + outputFile.getName() + "(" + fileSize / 1024 / 1024 + " Mb)");

                try(FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
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

                    System.out.println("File " + outputFile.getName() + " received");
                    if (totalBytesReceived == fileSize) {
                        out.write("SUC".getBytes(StandardCharsets.UTF_8));
                    } else {
                        out.write("ERR".getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    System.err.println("Error while transfer file: " + e.getMessage());
                    e.printStackTrace();
                }
            } catch (IOException e) {
                System.err.println("An error occurred while processing the client request: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error while closing the client socket");
                }
            }
        }
    }
}
