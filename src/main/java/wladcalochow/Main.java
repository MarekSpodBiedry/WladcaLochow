package wladcalochow;

import java.awt.GraphicsEnvironment;
import java.io.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import wladcalochow.silnik.ZapisGry;
import wladcalochow.ui.Aplikacja;

/**
 * Punkt wejścia. Uruchomiona w terminalu gra startuje od razu; uruchomiona bez terminala
 * (np. dwuklikiem w plik .jar) najpierw otwiera okno terminala i startuje w nim.
 *
 * Opcje: --ziarno LICZBA (powtarzalna rozgrywka), --zapis PLIK (inne miejsce zapisu),
 * --kolory 24|256 (wymuszenie trybu kolorów), --bez-okna (nie otwieraj nowego okna terminala),
 * --bez-aktualizacji (nie pytaj GitHuba o nową wersję), --wersja (wypisz numer wersji),
 * --sprawdz (autotest bez terminala, używany przez instalator).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path plikZapisu = ZapisGry.domyslnaSciezka();
        Long ziarno = null;
        boolean wOknie = false;
        boolean bezOkna = false;
        boolean aktualizacje = true;
        List<String> dlaOkna = new ArrayList<>();
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--ziarno" -> {
                        ziarno = Long.parseLong(args[++i]);
                        dlaOkna.addAll(List.of("--ziarno", args[i]));
                    }
                    case "--zapis" -> {
                        plikZapisu = Path.of(args[++i]);
                        dlaOkna.addAll(List.of("--zapis", plikZapisu.toAbsolutePath().toString()));
                    }
                    case Uruchamiacz.OPCJA_W_OKNIE -> wOknie = true;
                    case Uruchamiacz.OPCJA_KOLORY -> {
                        String tryb = args[++i];
                        if (!tryb.equals("24") && !tryb.equals("256")) {
                            throw new IllegalArgumentException("Tryb kolorów to 24 albo 256");
                        }
                        System.setProperty("wladca.kolory", tryb);
                        dlaOkna.addAll(List.of(Uruchamiacz.OPCJA_KOLORY, tryb));
                    }
                    case "--bez-okna" -> bezOkna = true;
                    case "--bez-aktualizacji" -> {
                        aktualizacje = false;
                        dlaOkna.add("--bez-aktualizacji");
                    }
                    case "--wersja" -> {
                        System.out.println("Wladca Lochow " + Wersja.numer());
                        System.exit(0);
                    }
                    case "--sprawdz" -> {
                        sprawdz();
                        return;
                    }
                    default -> throw new IllegalArgumentException("Nieznana opcja: " + args[i]);
                }
            }
        } catch (RuntimeException e) {
            zakonczZBledem("Użycie: java -jar wladca-lochow.jar [--ziarno LICZBA] [--zapis PLIK] [--kolory 24|256]"
                    + " [--bez-okna] [--bez-aktualizacji] [--wersja] [--sprawdz]",
                    wOknie, 2);
            return;
        }

        if (!wOknie && !bezOkna && !jestTerminal()) {
            if (Uruchamiacz.otworzOkno(dlaOkna)) {
                return;
            }
            pokazInstrukcje();
            System.exit(1);
            return;
        }

        if (wOknie) {
            // Prośba do terminala o powiększenie okna; terminale, które jej nie znają, po prostu ją pomijają.
            System.out.print("\u001b[8;" + Uruchamiacz.WIERSZE + ";" + Uruchamiacz.KOLUMNY + "t");
            System.out.flush();
        }
        aktualizacje &= Wersja.czyWydanie() && System.getenv("WLADCA_BEZ_AKTUALIZACJI") == null;
        try {
            new Aplikacja(plikZapisu, ziarno, aktualizacje).uruchom();
        } catch (IOException | RuntimeException e) {
            zakonczZBledem("Gra zakończyła się błędem: " + e.getMessage(), wOknie, 1);
        }
        // Jawne zakończenie: wątki sieciowe sprawdzania aktualizacji nie mogą trzymać otwartego okna.
        System.exit(0);
    }

    /**
     * Autotest dla instalatora: rozgrywa kilka dni bez terminala i sprawdza zapis gry.
     * Kod wyjścia 0 oznacza, że gra działa na tym komputerze.
     */
    private static void sprawdz() {
        try {
            Aplikacja.autotest();
            System.out.println("OK " + Wersja.numer());
            System.exit(0);
        } catch (Throwable e) {
            System.out.println("BLAD " + e);
            System.exit(1);
        }
    }

    /** Czy gra ma do dyspozycji prawdziwy terminal (a nie została uruchomiona np. dwuklikiem). */
    private static boolean jestTerminal() {
        Console konsola = System.console();
        if (konsola == null) {
            return false;
        }
        try {
            // Od Javy 22 konsola istnieje także bez terminala i trzeba ją o to zapytać.
            return (boolean) Console.class.getMethod("isTerminal").invoke(konsola);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    /** W osobnym oknie komunikat musi poczekać na Enter, bo inaczej okno zamknie się, zanim da się go przeczytać. */
    private static void zakonczZBledem(String komunikat, boolean wOknie, int kod) {
        System.err.println(komunikat);
        if (wOknie) {
            System.err.println("Naciśnij Enter, aby zamknąć okno.");
            try {
                while (true) {
                    int znak = System.in.read();
                    if (znak < 0 || znak == '\n' || znak == '\r') {
                        break;
                    }
                }
            } catch (IOException e) {
                // nic więcej nie da się zrobić
            }
        }
        System.exit(kod);
    }

    /** Gdy nie udało się otworzyć terminala, mówimy, jak uruchomić grę ręcznie. */
    private static void pokazInstrukcje() {
        Path jar = Uruchamiacz.plikJar();
        String polecenie = "java -jar " + (jar == null ? "wladca-lochow.jar" : "\"" + jar + "\"");
        String komunikat = "Władca Lochów działa w oknie terminala, a nie udało się go otworzyć automatycznie.\n\n"
                + "Otwórz terminal i wpisz:\n" + polecenie;
        System.err.println(komunikat);
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, komunikat, "Władca Lochów", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Throwable e) {
            // brak środowiska graficznego: komunikat został już wypisany
        }
    }
}
