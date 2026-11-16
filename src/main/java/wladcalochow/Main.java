package wladcalochow;

import java.io.IOException;
import java.nio.file.Path;
import wladcalochow.silnik.ZapisGry;
import wladcalochow.ui.Aplikacja;

/** Punkt wejścia. Opcje: --ziarno LICZBA (powtarzalna rozgrywka), --zapis PLIK (inne miejsce zapisu). */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path plikZapisu = ZapisGry.domyslnaSciezka();
        Long ziarno = null;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--ziarno" -> ziarno = Long.parseLong(args[++i]);
                    case "--zapis" -> plikZapisu = Path.of(args[++i]);
                    default -> throw new IllegalArgumentException("Nieznana opcja: " + args[i]);
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Użycie: java -jar wladca-lochow.jar [--ziarno LICZBA] [--zapis PLIK]");
            System.exit(2);
        }
        try {
            new Aplikacja(plikZapisu, ziarno).uruchom();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
