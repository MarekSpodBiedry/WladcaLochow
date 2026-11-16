package wladcalochow.silnik;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Zapis stanu gry do plików (serializacja Javy). Gra ma {@value #SLOTY} slotów zapisu, każdy w osobnym pliku.
 * Gra sama nigdy nie kasuje zapisu: przegrana rozgrywka zostaje w swoim slocie. Slot zmienia treść tylko wtedy,
 * gdy gracz sam wybierze go dla nowej gry.
 */
public final class ZapisGry {

    /** Liczba slotów zapisu. */
    public static final int SLOTY = 10;

    private ZapisGry() {
    }

    /** Stan jednego slotu. Plik może istnieć, a mimo to nie dać się odczytać (np. zapis ze starszej wersji gry). */
    public record Slot(int numer, Path plik, boolean zajety, Optional<Gra> gra, FileTime czas) {

        /** Czy w slocie jest rozgrywka, którą da się kontynuować. */
        public boolean doKontynuacji() {
            return gra.isPresent() && !gra.get().czyKoniecGry() && gra.get().getDzien() != null;
        }
    }

    /**
     * Folder ze slotami: na Windows w folderze gry w AppData (tam, gdzie instalator kładzie grę),
     * na pozostałych systemach w ukrytym katalogu w folderze domowym.
     * Zapis z wersji bez slotów (jeden plik zapis.dat) trafia do slotu 1; stary plik zostaje na miejscu.
     */
    public static Path domyslnyFolder() {
        Path dom = Path.of(System.getProperty("user.home"), ".wladcalochow");
        String appData = System.getenv("LOCALAPPDATA");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path baza = windows && appData != null && !appData.isBlank() ? Path.of(appData, "WladcaLochow") : dom;
        Path folder = baza.resolve("zapisy");
        przeniesStaryZapis(folder, List.of(baza.resolve("zapis.dat"), dom.resolve("zapis.dat")));
        return folder;
    }

    /** Kopiuje pierwszy istniejący stary zapis do slotu 1, jeśli folder slotów jest jeszcze pusty. */
    static void przeniesStaryZapis(Path folder, List<Path> stare) {
        try {
            for (int numer = 1; numer <= SLOTY; numer++) {
                if (Files.exists(plikSlotu(folder, numer))) {
                    return;
                }
            }
            for (Path stary : stare) {
                if (Files.isRegularFile(stary)) {
                    Files.createDirectories(folder);
                    Files.copy(stary, plikSlotu(folder, 1), StandardCopyOption.COPY_ATTRIBUTES);
                    return;
                }
            }
        } catch (IOException e) {
            // stary zapis zostaje na swoim miejscu; sloty zaczynają się puste
        }
    }

    public static Path plikSlotu(Path folder, int numer) {
        if (numer < 1 || numer > SLOTY) {
            throw new IllegalArgumentException("Nie ma slotu " + numer);
        }
        return folder.resolve(String.format("slot-%02d.dat", numer));
    }

    /** Wszystkie sloty po kolei, od 1 do {@value #SLOTY}. */
    public static List<Slot> sloty(Path folder) {
        List<Slot> wynik = new ArrayList<>();
        for (int numer = 1; numer <= SLOTY; numer++) {
            Path plik = plikSlotu(folder, numer);
            boolean zajety = Files.isRegularFile(plik);
            FileTime czas = null;
            if (zajety) {
                try {
                    czas = Files.getLastModifiedTime(plik);
                } catch (IOException e) {
                    // brak daty nie przeszkadza
                }
            }
            wynik.add(new Slot(numer, plik, zajety, zajety ? wczytaj(plik) : Optional.empty(), czas));
        }
        return wynik;
    }

    /** Pierwszy wolny slot. */
    public static Optional<Slot> wolnySlot(List<Slot> sloty) {
        return sloty.stream().filter(slot -> !slot.zajety()).findFirst();
    }

    /** Ostatnio zapisana rozgrywka, którą da się kontynuować. */
    public static Optional<Slot> ostatniDoKontynuacji(List<Slot> sloty) {
        return sloty.stream()
                .filter(Slot::doKontynuacji)
                .max(Comparator.comparing(slot -> slot.czas() == null ? FileTime.fromMillis(0) : slot.czas()));
    }

    public static void zapisz(Gra gra, Path plik) throws IOException {
        if (plik.getParent() != null) {
            Files.createDirectories(plik.getParent());
        }
        Path tymczasowy = plik.resolveSibling(plik.getFileName() + ".tmp");
        try (ObjectOutputStream wyjscie = new ObjectOutputStream(Files.newOutputStream(tymczasowy))) {
            wyjscie.writeObject(gra);
        }
        Files.move(tymczasowy, plik, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Wczytuje zapis. Brak pliku albo zapis z innej wersji gry daje pusty wynik. */
    public static Optional<Gra> wczytaj(Path plik) {
        if (!Files.isRegularFile(plik)) {
            return Optional.empty();
        }
        try (ObjectInputStream wejscie = new ObjectInputStream(Files.newInputStream(plik))) {
            return wejscie.readObject() instanceof Gra gra ? Optional.of(gra) : Optional.empty();
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
