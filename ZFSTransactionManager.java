import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class ZFSTransactionManager {
    private final String zfsDataset;
    private final String basePath;
    private final Map<String, String> fileHashes = new HashMap<>();
    private String snapshotName;
    private final Set<String> modifiedFiles = new HashSet<>(); // Neue Liste für geänderte Dateien

    public ZFSTransactionManager(String zfsDataset, String basePath) {
        this.zfsDataset = zfsDataset;
        this.basePath = basePath;
    }

    public void beginTransaction() throws IOException {
        snapshotName = "snapshot_" + System.currentTimeMillis();
        executeCommand("sudo zfs snapshot " + zfsDataset + "@" + snapshotName);
        fileHashes.clear();
        captureFileStates();
    }

    public void commitTransaction() throws IOException {
        if (detectConflicts()) {
            rollbackTransaction();
            throw new IOException("Conflict detected, transaction rolled back.");
        }
        deleteSnapshot();
    }

    public void rollbackTransaction() throws IOException {
        executeCommand("sudo zfs rollback " + zfsDataset + "@" + snapshotName);
        deleteSnapshot();
    }

    private void deleteSnapshot() throws IOException {
        executeCommand("sudo zfs destroy " + zfsDataset + "@" + snapshotName);
    }

    private void captureFileStates() throws IOException {
        Files.walk(Paths.get(basePath)).filter(Files::isRegularFile).forEach(file -> {
            try {
                // Nur speichern, wenn die Datei noch nicht in der Hash-Liste ist (erste Erfassung)
                if (!fileHashes.containsKey(file.toString())) {
                    fileHashes.put(file.toString(), computeFileHash(file));
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        });
    }


    private boolean detectConflicts() throws IOException {
        for (Map.Entry<String, String> entry : fileHashes.entrySet()) {
            Path file = Paths.get(entry.getKey());
            if (Files.exists(file)) {
                try {
                    String currentHash = computeFileHash(file);

                    // Falls die Datei in dieser Transaktion geändert wurde, keinen Konflikt erkennen
                    if (modifiedFiles.contains(file.toString())) {
                        continue;
                    }

                    if (!entry.getValue().equals(currentHash)) {
                        System.out.println("Konflikt erkannt bei: " + file);
                        return true;
                    }
                } catch (IOException | NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private String computeFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = digest.digest(bytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }


    private void executeCommand(String command) throws IOException {
        Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", command});
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Command execution failed: " + command);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Command execution interrupted: " + command, e);
        }
    }

    public void writeFile(String filePath, String content) throws IOException {
        Path path = Paths.get(basePath, filePath);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Datei als "modifiziert" markieren
        modifiedFiles.add(filePath);
    }

    public String readFile(String filePath) throws IOException {
        Path path = Paths.get(basePath, filePath);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public void deleteFile(String filePath) throws IOException {
        Path path = Paths.get(basePath, filePath);
        Files.deleteIfExists(path);
    }

    public static void main(String[] args) {
        String dataset = "mypool/mydataset";
        String basePath = "/mypool/mydataset";
        ZFSTransactionManager manager = new ZFSTransactionManager(dataset, basePath);

        try {
            manager.beginTransaction();
            System.out.println("Transaction started.");

            manager.writeFile("test.txt", "Hello, ZFS!");
            System.out.println("File written: test.txt");

            String content = manager.readFile("test.txt");
            System.out.println("File content: " + content);

            manager.commitTransaction();
            System.out.println("Transaction committed.");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
