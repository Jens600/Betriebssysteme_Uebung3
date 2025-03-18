import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class ValidationTool {
    private static final String DATASET = "mypool/mydataset";
    private static final String BASE_PATH = "/mypool/mydataset";
    private static final ZFSTransactionManager manager = new ZFSTransactionManager(DATASET, BASE_PATH);
    private static final int NUM_THREADS = 10; // Anzahl paralleler Transaktionen
    private static final int NUM_OPERATIONS = 50; // Anzahl Gesamtoperationen
    private static final Random random = new Random();

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        List<Future<Boolean>> results = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            results.add(executor.submit(ValidationTool::executeRandomTransaction));
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);

        int successCount = 0, rollbackCount = 0;
        for (Future<Boolean> result : results) {
            try {
                if (result.get()) {
                    successCount++;
                } else {
                    rollbackCount++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("Validierung abgeschlossen.");
        System.out.println("Erfolgreiche Transaktionen: " + successCount);
        System.out.println("Rollback-Transaktionen: " + rollbackCount);
        System.out.println("Konfliktrate: " + (rollbackCount * 100.0 / NUM_OPERATIONS) + "%");
        System.out.println("Gesamtdauer: " + totalTime + " ms");
    }

    private static boolean executeRandomTransaction() {
        String fileName = "test_" + random.nextInt(5) + ".txt"; // Zufällige Datei
        String filePath = BASE_PATH + "/" + fileName;

        try {
            manager.beginTransaction();
            int operation = random.nextInt(3);

            switch (operation) {
                case 0: // Lesen
                    manager.readFile(fileName);
                    break;
                case 1: // Schreiben
                    String content = "Zufälliger Inhalt: " + UUID.randomUUID();
                    manager.writeFile(fileName, content);
                    break;
                case 2: // Löschen
                    manager.deleteFile(fileName);
                    break;
            }

            manager.commitTransaction();
            return true;
        } catch (IOException e) {
            System.err.println("Rollback wegen Konflikt: " + e.getMessage());
            try {
                manager.rollbackTransaction();
            } catch (IOException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            return false;
        }
    }
}
