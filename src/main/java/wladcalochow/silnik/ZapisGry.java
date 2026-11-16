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

    public static Path domyslnaSciezka() {
        return Path.of(System.getProperty("user.home"), ".wladcalochow", "zapis.dat");
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
