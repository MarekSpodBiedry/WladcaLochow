package wladcalochow.silnik;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Pełny zapis stanu gry do pliku (serializacja Javy). */
public final class ZapisGry {

    private ZapisGry() {
    }

    /**
     * Miejsce zapisu: na Windows folder gry w AppData (ten sam, do którego trafia gra z instalatora),
     * na pozostałych systemach ukryty katalog w folderze domowym.
     */
    public static Path domyslnaSciezka() {
        Path stara = Path.of(System.getProperty("user.home"), ".wladcalochow", "zapis.dat");
        String appData = System.getenv("LOCALAPPDATA");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!windows || appData == null || appData.isBlank()) {
            return stara;
        }
        Path nowa = Path.of(appData, "WladcaLochow", "zapis.dat");
        try {
            if (!Files.exists(nowa) && Files.isRegularFile(stara)) {
                Files.createDirectories(nowa.getParent());
                Files.copy(stara, nowa);
            }
        } catch (IOException e) {
            // stary zapis zostaje na miejscu; gra zacznie w nowym folderze od zera
        }
        return nowa;
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

    public static boolean istnieje(Path plik) {
        return Files.isRegularFile(plik);
    }

    public static void usun(Path plik) {
        try {
            Files.deleteIfExists(plik);
        } catch (IOException e) {
            // brak możliwości usunięcia zapisu nie przerywa gry
        }
    }
}
