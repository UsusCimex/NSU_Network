package ru.nsu;

import java.net.*;
import java.io.*;
import java.util.*;

public class Multicast {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: gradle run --args=\"IP address\"");
            System.exit(1);
        }

        String multicastGroup = args[0];
        InetAddress multicastAddress;
        int multicastPort = 21212; // Порт для multicast
        String userListFileName = "userList.txt"; // Имя файла для списка пользователей

        try {
            multicastAddress = InetAddress.getByName(multicastGroup);

            // Определяем, IPv4 или IPv6 адрес
            if (multicastAddress instanceof Inet4Address) {
                System.out.println("Used IPv4");
            } else if (multicastAddress instanceof Inet6Address) {
                System.out.println("Used IPv6");
            } else {
                System.out.println("Unknown address type");
                System.exit(1);
            }

            MulticastSocket socket = new MulticastSocket(multicastPort);
            socket.joinGroup(multicastAddress);
            System.out.println("Connected to group " + multicastGroup + ":" + multicastPort);

            Map<String, Long> userList = new HashMap<>();
            Map<String, Long> oldUserList = new HashMap<>();

            // Создаём поток, который будет отправлять сообщения каждые 0.5 сек
            Thread senderThread = new Thread(() -> {
                try {
                    while (true) {
                        sendMulticastMessage(socket, multicastAddress, multicastPort);
                        Thread.sleep(500);
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
            senderThread.start();

            long timeout = 5000; // 5 секунд без активности
            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String senderAddress = packet.getAddress().getHostAddress();
                userList.put(senderAddress, System.currentTimeMillis());

                // Проверяем и удаляем неактивных пользователей
                List<String> inactiveUsers = new ArrayList<>();
                for (Map.Entry<String, Long> entry : userList.entrySet()) {
                    if (System.currentTimeMillis() - entry.getValue() > timeout) {
                        inactiveUsers.add(entry.getKey());
                    }
                }
                for (String inactiveUser : inactiveUsers) {
                    userList.remove(inactiveUser);
                }

                // Обновляем список пользователей в файле
                if (!oldUserList.keySet().equals(userList.keySet())) {
                    updateUserList(userListFileName, userList);
                    System.out.println("Current users: " + userList.keySet());

                    oldUserList = new HashMap<>(userList);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendMulticastMessage(MulticastSocket socket, InetAddress multicastAddress, int multicastPort) throws IOException {
        String message = "Hello world!";
        DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), multicastAddress, multicastPort);
        socket.send(packet);
    }

    private static void updateUserList(String fileName, Map<String, Long> userList) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))) {
            for (String address : userList.keySet()) {
                bw.write(address);
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
