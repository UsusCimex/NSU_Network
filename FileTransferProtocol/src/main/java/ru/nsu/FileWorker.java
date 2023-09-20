package ru.nsu;

public abstract class FileWorker {
    // Методы для получения имени файла без расширения и получения расширения файла
    public static String removeFileExtension(String fileName) {
        if (fileName.contains(".")) {
            return fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return fileName;
    }

    public static String getFileExtension(String fileName) {
        if (fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1);
        }
        return "";
    }
}
