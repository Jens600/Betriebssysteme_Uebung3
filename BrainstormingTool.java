import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class BrainstormingTool {
    private static final String DATASET = "mypool/mydataset";
    private static final String BASE_PATH = "/mypool/mydataset";
    private static final ZFSTransactionManager manager = new ZFSTransactionManager(DATASET, BASE_PATH);

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nBrainstorming Tool");
            System.out.println("1. Neue Idee hinzufügen");
            System.out.println("2. Idee lesen");
            System.out.println("3. Idee kommentieren");
            System.out.println("4. Beenden");

            int choice = -1;
            while (choice == -1) {
                System.out.print("Auswahl: ");
                if (scanner.hasNextInt()) {
                    choice = scanner.nextInt();
                } else {
                    System.out.println("Ungültige Eingabe! Bitte geben Sie eine Zahl ein.");
                    scanner.next(); // Falsche Eingabe entfernen
                }
            }
            scanner.nextLine(); // Scanner-Puffer leeren

            switch (choice) {
                case 1:
                    addIdea(scanner);
                    break;
                case 2:
                    readIdea(scanner);
                    break;
                case 3:
                    commentIdea(scanner);
                    break;
                case 4:
                    System.out.println("Programm beendet.");
                    return;
                default:
                    System.out.println("Ungültige Eingabe.");
            }
        }
    }

    private static void addIdea(Scanner scanner) {
        System.out.print("Titel der neuen Idee: ");
        String title = scanner.nextLine().replaceAll("\\s+", "_");
        String filePath = title + ".txt";

        try {
            manager.beginTransaction();
            System.out.println("Geben Sie die Idee ein (mehrzeilig, Ende mit einer Leerzeile):");
            String content = readMultiLineInput(scanner);
            manager.writeFile(filePath, content);
            manager.commitTransaction();
            System.out.println("Idee gespeichert.");
        } catch (IOException e) {
            System.err.println("Fehler: " + e.getMessage());
        }
    }

    private static void readIdea(Scanner scanner) {
        System.out.print("Titel der Idee: ");
        String title = scanner.nextLine().replaceAll("\\s+", "_");
        String filePath = title + ".txt";

        try {
            String content = manager.readFile(filePath);
            System.out.println("\nInhalt der Idee:");
            System.out.println(content);
        } catch (IOException e) {
            System.err.println("Fehler: " + e.getMessage());
        }
    }

    private static void commentIdea(Scanner scanner) {
        System.out.print("Titel der Idee: ");
        String title = scanner.nextLine().replaceAll("\\s+", "_");
        String filePath = title + ".txt";

        try {
            manager.beginTransaction();

            // Dateiinhalt lesen und bestehende Zeilenumbrüche erhalten
            String existingContent = manager.readFile(filePath).stripTrailing();
            System.out.println("Geben Sie Ihren Kommentar ein (mehrzeilig, Ende mit einer Leerzeile):");

            String comment = "\nKommentar:\n" + readMultiLineInput(scanner).stripTrailing();

            // Datei mit zusätzlichem Kommentar speichern
            manager.writeFile(filePath, existingContent + "\n" + comment);
            System.out.println("Kommentar hinzugefügt.");
        } catch (IOException e) {
            System.err.println("Fehler: " + e.getMessage());
            try {
                manager.rollbackTransaction();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    private static String readMultiLineInput(Scanner scanner) {
        StringBuilder input = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).isEmpty()) {
            input.append(line).append("\n");
        }
        return input.toString();
    }
}

