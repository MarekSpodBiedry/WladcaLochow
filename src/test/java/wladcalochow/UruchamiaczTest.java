package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wladcalochow.Uruchamiacz.SystemOperacyjny;

class UruchamiaczTest {

    private static final List<String> GRA = List.of("/opt/java/bin/java", "-jar", "/gry/wladca-lochow.jar",
            Uruchamiacz.OPCJA_W_OKNIE);

    @Test
    void polecenieGryUruchamiaTenSamJarZOpcjaOkna() {
        List<String> polecenie = Uruchamiacz.polecenieGry(Path.of("/opt/java/bin/java"),
                Path.of("/gry/wladca-lochow.jar"), List.of("--ziarno", "7"));
        assertEquals(List.of("/opt/java/bin/java", "-jar", "/gry/wladca-lochow.jar", "--w-oknie", "--ziarno", "7"),
                polecenie);
    }

    @Test
    void naWindowsNajpierwWindowsTerminalPotemZwyklaKonsola() {
        List<List<String>> polecenia = Uruchamiacz.polecenia(SystemOperacyjny.WINDOWS, GRA, program -> true);
        assertEquals(2, polecenia.size());
        assertEquals("wt.exe", polecenia.get(0).get(0));
        assertEquals(List.of("cmd.exe", "/c", "start", "Wladca Lochow", "/MAX", "/opt/java/bin/java", "-jar",
                "/gry/wladca-lochow.jar", "--w-oknie"), polecenia.get(1));

        List<List<String>> bezWt = Uruchamiacz.polecenia(SystemOperacyjny.WINDOWS, GRA, program -> false);
        assertEquals(1, bezWt.size());
        assertEquals("cmd.exe", bezWt.get(0).get(0));
    }

    @Test
    void naLinuksieTylkoZainstalowaneTerminaleWKolejnosciOdNajlepszego() {
        Set<String> zainstalowane = Set.of("xterm", "konsole");
        List<List<String>> polecenia = Uruchamiacz.polecenia(SystemOperacyjny.LINUX, GRA, zainstalowane::contains);
        assertEquals(2, polecenia.size());
        assertEquals(List.of("konsole", "-e", "/opt/java/bin/java", "-jar", "/gry/wladca-lochow.jar", "--w-oknie"),
                polecenia.get(0));
        assertEquals("xterm", polecenia.get(1).get(0));
        assertTrue(polecenia.get(1).contains("124x38"), "xterm dostaje rozmiar okna");
        assertEquals(List.of("--w-oknie", "--kolory", "24"),
                polecenia.get(1).subList(polecenia.get(1).size() - 3, polecenia.get(1).size()),
                "xterm nie ogłasza pełnych kolorów, więc gra dostaje je wprost");

        assertTrue(Uruchamiacz.polecenia(SystemOperacyjny.LINUX, GRA, program -> false).isEmpty());
    }

    @Test
    void naMacuTerminalDostajePoleceniePowlokiIRozmiar() {
        List<List<String>> polecenia = Uruchamiacz.polecenia(SystemOperacyjny.MACOS, GRA, program -> false);
        assertEquals(1, polecenia.size());
        List<String> osascript = polecenia.get(0);
        assertEquals("osascript", osascript.get(0));
        String skrypt = String.join("\n", osascript);
        assertTrue(skrypt.contains("do script \"'/opt/java/bin/java' '-jar' '/gry/wladca-lochow.jar' '--w-oknie'; exit\""));
        assertTrue(skrypt.contains("set number of columns of karta to 124"));
        assertTrue(skrypt.contains("set number of rows of karta to 38"));
    }

    @Test
    void sciezkiZeSpacjamiICudzyslowamiSaZabezpieczone() {
        assertEquals("'/Moje Gry/java' '-jar' '/a/to'\\''jest.jar'",
                Uruchamiacz.dlaPowloki(List.of("/Moje Gry/java", "-jar", "/a/to'jest.jar")));
        assertEquals("tekst \\\"w cudzysłowie\\\" i \\\\ ukośnik", Uruchamiacz.dlaAppleScript("tekst \"w cudzysłowie\" i \\ ukośnik"));
    }
}
