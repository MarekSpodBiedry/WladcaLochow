package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InstalatorTest {

    private static final Path INSTALATOR = Path.of("install.ps1");

    /** Instalator musi działać niezależnie od kodowania, w jakim PowerShell go wczyta. */
    @Test
    void publikowanyInstalatorToCzysteAscii() throws Exception {
        byte[] bajty = Files.readAllBytes(INSTALATOR);
        for (int i = 0; i < bajty.length; i++) {
            assertTrue(bajty[i] >= 0, "znak spoza ASCII w install.ps1, bajt numer " + i);
        }
    }

    /** Ikona dołączana do wydań musi być prawdziwym plikiem ICO z kilkoma rozmiarami. */
    @Test
    void ikonaJestPoprawnymPlikiemIco() throws Exception {
        byte[] bajty = Files.readAllBytes(Path.of("zasoby", "ikona.ico"));
        // nagłówek ICO: dwa bajty zerowe, typ 1 (ikona), potem liczba obrazów
        assertEquals(0, bajty[0]);
        assertEquals(0, bajty[1]);
        assertEquals(1, bajty[2]);
        assertEquals(0, bajty[3]);
        int obrazy = (bajty[4] & 0xFF) | ((bajty[5] & 0xFF) << 8);
        assertTrue(obrazy >= 4, "ikona powinna mieć kilka rozmiarów, a ma " + obrazy);
        assertEquals(16, bajty[6] & 0xFF, "pierwszy rozmiar to 16 pikseli");
    }

    /** Gra i instalator muszą mówić o tym samym repozytorium, pliku i opcji autotestu. */
    @Test
    void instalatorIGraSaZeSobaZgodne() throws Exception {
        String skrypt = Files.readString(INSTALATOR, StandardCharsets.US_ASCII);
        assertTrue(skrypt.contains("$Repo = '" + Aktualizacje.REPOZYTORIUM + "'"));
        assertTrue(skrypt.contains("'wladca-lochow.jar'"));
        assertTrue(skrypt.contains("--sprawdz"));
        assertEquals("https://raw.githubusercontent.com/" + Aktualizacje.REPOZYTORIUM + "/main/install.ps1",
                Aktualizacje.ADRES_INSTALATORA);
        assertTrue(skrypt.contains(Aktualizacje.POLECENIE_INSTALATORA),
                "nagłówek instalatora podaje to samo polecenie, które gra pokazuje graczowi");
    }
}
