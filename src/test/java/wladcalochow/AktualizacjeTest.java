package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import wladcalochow.ui.Aplikacja;

class AktualizacjeTest {

    @Test
    void numerWersjiJestWyciaganyZOdpowiedziGitHuba() {
        String json = "{\"url\":\"x\",\"tag_name\":\"v1.12.3\",\"name\":\"Władca Lochów 1.12.3\"}";
        assertEquals(Optional.of("1.12.3"), Aktualizacje.wersjaZOdpowiedzi(json));
        assertEquals(Optional.of("2.0"), Aktualizacje.wersjaZOdpowiedzi("{ \"tag_name\" : \"2.0\" }"));
        assertEquals(Optional.empty(), Aktualizacje.wersjaZOdpowiedzi("{\"message\":\"Not Found\"}"));
        assertEquals(Optional.empty(), Aktualizacje.wersjaZOdpowiedzi(null));
    }

    @Test
    void wersjeSaPorownywaneLiczbowoANieAlfabetycznie() {
        assertTrue(Aktualizacje.czyNowsza("1.10.0", "1.9.3"));
        assertTrue(Aktualizacje.czyNowsza("2.0", "1.99.99"));
        assertTrue(Aktualizacje.czyNowsza("1.0.1", "1.0"));
        assertFalse(Aktualizacje.czyNowsza("1.0.0", "1.0.0"));
        assertFalse(Aktualizacje.czyNowsza("1.0", "1.0.0"));
        assertFalse(Aktualizacje.czyNowsza("0.9", "1.0"));
        assertFalse(Aktualizacje.czyNowsza("1.1.0", Wersja.ROBOCZA), "wersja robocza nie jest porównywalna");
    }

    @Test
    void aktualizacjaNaWindowsOtwieraInstalatorWNowymOknie() {
        List<String> polecenie = Uruchamiacz.polecenieAktualizacji();
        assertEquals(List.of("cmd.exe", "/c", "start"), polecenie.subList(0, 3));
        assertTrue(polecenie.contains("powershell.exe"));
        // Przycisk „Aktualizuj” bierze instalator prosto z GitHuba, bez zależności od krótkiego linku.
        assertEquals("irm https://raw.githubusercontent.com/MarekSpodBiedry/WladcaLochow/main/install.ps1 | iex",
                polecenie.get(polecenie.size() - 1));
    }

    @Test
    void graczWidziKrotkiLinkZReadme() throws Exception {
        assertEquals("irm https://msblink.pl/WladcaLochow | iex", Aktualizacje.POLECENIE_INSTALATORA);
        String readme = java.nio.file.Files.readString(java.nio.file.Path.of("README.md"));
        assertTrue(readme.contains(Aktualizacje.POLECENIE_INSTALATORA), "README i gra podają to samo polecenie");
    }

    @Test
    void autotestGryPrzechodzi() throws Exception {
        Aplikacja.autotest();
    }
}
