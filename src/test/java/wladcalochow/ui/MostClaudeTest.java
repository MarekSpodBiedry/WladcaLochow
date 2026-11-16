package wladcalochow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.silnik.ZapisGry;

/** Claude gra za Władcę przez pliki: test udaje Claude'a, który czyta stan i odpisuje decyzjami. */
class MostClaudeTest {

    private static final int SZER = Aplikacja.MIN_SZER + 16;
    private static final int WYS = Aplikacja.MIN_WYS + 10;

    private static String stan(Path claude) throws Exception {
        return Files.readString(claude.resolve(MostClaude.PLIK_STANU), StandardCharsets.UTF_8);
    }

    private static String id(String stan) {
        return stan.lines().filter(l -> l.startsWith("id: ")).findFirst().orElseThrow().substring(4);
    }

    private static void odpisz(Path claude, String tresc) throws Exception {
        Files.writeString(claude.resolve(MostClaude.PLIK_DECYZJI), tresc, StandardCharsets.UTF_8);
    }

    private static void znak(Aplikacja aplikacja, char znak) {
        aplikacja.obsluz(new Wejscie.Znak(znak));
        aplikacja.rysuj();
    }

    @Test
    void claudeWybieraMinistrowIWydajeRozkazy(@TempDir Path katalog) throws Exception {
        Path claude = katalog.resolve("claude");
        Aplikacja aplikacja = new Aplikacja(new KonsolaBezEkranu(SZER, WYS), katalog.resolve("zapisy"), 5L, claude);
        aplikacja.rysuj();
        znak(aplikacja, 'c');
        assertTrue(aplikacja.klatka().tekst().contains("Claude: wł."));
        znak(aplikacja, 'n');

        String start = stan(claude);
        assertTrue(start.contains("typ: START"));
        assertTrue(start.contains("MF3:") && start.contains("MP3:"));
        assertTrue(aplikacja.klatka().tekst().contains("Claude wybiera ministrów"));

        // Odpowiedź z innym identyfikatorem (np. na wcześniejszą prośbę) jest pomijana.
        odpisz(claude, "id: stary\nstart: MF1 MP1\n");
        aplikacja.sprawdzClaude();
        assertTrue(aplikacja.gra().getDzien() == null, "gra czeka dalej");

        odpisz(claude, "id: " + id(start) + "\nstart: MF2 MP3\npowod: tak wybieram\n");
        aplikacja.sprawdzClaude();
        aplikacja.rysuj();
        assertNotNull(aplikacja.gra().getDzien());
        assertEquals(aplikacja.gra().getStartowiFinansow().get(1), aplikacja.gra().getLoch().getMinisterFinansow());
        assertEquals(aplikacja.gra().getStartowiPracy().get(2), aplikacja.gra().getLoch().getMinisterPracy());

        String poranek = stan(claude);
        assertTrue(poranek.contains("typ: PORANEK"));
        assertTrue(poranek.contains("Dzień: 1"));
        assertTrue(poranek.contains("POSTAWA ODWOD |"));
        assertTrue(poranek.contains("PREMIA P1 |"));
        assertFalse(poranek.contains("bohaterowie: "), "stan nie zdradza dokładnego składu bohaterów");
        assertFalse(aplikacja.dostepneSkroty().contains('u'), "w trakcie czekania gracz nie wydaje rozkazów");

        odpisz(claude, "id: " + id(poranek) + "\nrozkaz: POSTAWA ODWOD\nrozkaz: SKOK W BOK\n"
                + "powod: odwód chroni słabe potwory\nna dwie linie\n");
        aplikacja.sprawdzClaude();
        aplikacja.rysuj();
        assertEquals(Postawa.ODWOD, aplikacja.gra().getLoch().getPostawa());
        String ekran = aplikacja.klatka().tekst();
        assertTrue(ekran.contains("Decyzje Claude'a"));
        assertTrue(ekran.contains("Wykonano: Postawa: Odwód"));
        assertTrue(ekran.contains("Odrzucono"));
        assertTrue(Files.readString(claude.resolve(MostClaude.PLIK_HISTORII), StandardCharsets.UTF_8)
                .contains("odwód chroni słabe potwory"));
        assertTrue(Files.exists(ZapisGry.plikSlotu(katalog.resolve("zapisy"), 1)));

        // Po zamknięciu okna gracz sam rusza dzień, a następny poranek znowu trafia do Claude'a.
        aplikacja.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER));
        for (int krok = 0; krok < 400 && aplikacja.gra().getNumerDnia() < 2; krok++) {
            aplikacja.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER));
            aplikacja.rysuj();
        }
        assertEquals(2, aplikacja.gra().getNumerDnia());
        assertTrue(stan(claude).contains("Dzień: 2"));
        assertTrue(stan(claude).contains("Dzień 1"), "dziennik z wczoraj trafia do stanu");
    }

    @Test
    void wylaczenieClaudeaPrzestajeCzekac(@TempDir Path katalog) throws Exception {
        Path claude = katalog.resolve("claude");
        Aplikacja aplikacja = new Aplikacja(new KonsolaBezEkranu(SZER, WYS), katalog.resolve("zapisy"), 6L, claude);
        aplikacja.rysuj();
        znak(aplikacja, 'c');
        znak(aplikacja, 'n');
        odpisz(claude, "id: " + id(stan(claude)) + "\nstart: MF1 MP1\n");
        aplikacja.sprawdzClaude();
        aplikacja.rysuj();
        assertTrue(aplikacja.klatka().tekst().contains("Czekam na decyzje Claude'a"));
        znak(aplikacja, 'c');
        assertTrue(aplikacja.dostepneSkroty().contains('u'), "po wyłączeniu Claude'a gracz znowu wydaje rozkazy");
    }
}
