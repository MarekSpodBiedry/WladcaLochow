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

    /** Gra i instalator muszą mówić o tym samym repozytorium, pliku i opcji autotestu. */
    @Test
    void instalatorIGraSaZeSobaZgodne() throws Exception {
        String skrypt = Files.readString(INSTALATOR, StandardCharsets.US_ASCII);
        assertTrue(skrypt.contains("$Repo = '" + Aktualizacje.REPOZYTORIUM + "'"));
        assertTrue(skrypt.contains("'wladca-lochow.jar'"));
        assertTrue(skrypt.contains("--sprawdz"));
        assertEquals("https://raw.githubusercontent.com/" + Aktualizacje.REPOZYTORIUM + "/main/install.ps1",
                Aktualizacje.ADRES_INSTALATORA);
        assertTrue(skrypt.contains("irm " + Aktualizacje.ADRES_INSTALATORA + " | iex"),
                "nagłówek instalatora podaje to samo polecenie, którego używa gra przy aktualizacji");
    }
}
