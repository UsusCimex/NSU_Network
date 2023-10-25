package ru.nsu;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class FileTransferClient {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java FileTransferClient \"file path\" \"dst IP\" \"dst port\"");
            return;
        }

        String filePath = args[0];
        String serverIP = args[1];
        int serverPort = Integer.parseInt(args[2]);

        try (Socket socket = new Socket(serverIP, serverPort);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Start upload file!");

            File fileToSend = new File(filePath);
            long fileSize = fileToSend.length();

            out.writeShort(fileToSend.getName().length());
            out.write(fileToSend.getName().getBytes(StandardCharsets.UTF_8));
            out.writeLong(fileSize);

            try(FileInputStream fileInputStream = new FileInputStream(fileToSend)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                System.err.println("Error reading or sending the file: " + e.getMessage());
                e.printStackTrace();
            }

            byte[] answerBytes = new byte[3];
            in.readFully(answerBytes);
            String answer = new String(answerBytes, StandardCharsets.UTF_8);
            System.out.println("Server answer: " + answer); // SUC = success, ERR - error
        } catch (IOException e) {
            System.err.println("Error establishing or closing the connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
