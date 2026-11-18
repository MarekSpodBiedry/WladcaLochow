package wladcalochow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wladcalochow.silnik.Gra;

/** Myszka: odczyt zgłoszeń terminala, najechanie, kliknięcie i kółko. */
class MyszTest {

    @Test
    void zgloszeniaTerminalaZamieniajaSieNaZdarzenia() {
        // Współrzędne terminala liczone od 1, gry od 0.
        assertEquals(new Wejscie.Klik(9, 4), TerminalGry.klik(0, 10, 5, true));
        assertEquals(new Wejscie.Ruch(9, 4), TerminalGry.klik(35, 10, 5, true), "ruch bez przycisku (tryb 1003)");
        assertEquals(new Wejscie.Ruch(9, 4), TerminalGry.klik(32, 10, 5, true), "przeciąganie z wciśniętym lewym");
        assertEquals(new Wejscie.Kolko(-1, 9, 4), TerminalGry.klik(64, 10, 5, true));
        assertEquals(new Wejscie.Kolko(1, 9, 4), TerminalGry.klik(65, 10, 5, true));
        assertEquals(new Wejscie.Ruch(9, 4), TerminalGry.klik(3, 10, 5, true), "puszczenie w starym formacie");
        assertEquals(new Wejscie.Ruch(9, 4), TerminalGry.klik(0, 10, 5, false), "puszczenie w formacie SGR");
        assertEquals(new Wejscie.Brak(), TerminalGry.klik(0, 0, 5, true));
    }

    private static Aplikacja poranek(Path katalog) {
        Aplikacja a = new Aplikacja(new KonsolaBezEkranu(124, 36), katalog, 7L, katalog.resolve("claude"));
        Gra gra = Gra.nowa(7);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        a.pokazGre(gra, 0);
        a.rysuj();
        return a;
    }

    /** Wiersz ekranu, w którym stoi znacznik zaznaczonej opcji ▶. */
    private static int wierszZaznaczenia(Aplikacja a) {
        String[] linie = a.klatka().tekst().split("\n");
        for (int y = 0; y < linie.length; y++) {
            // „▶ 1” itd.: znacznik opcji w oknie (sam ▶ jest też w fladze na scenie).
            if (linie[y].matches(".*▶ [1-9].*")) {
                return y;
            }
        }
        return -1;
    }

    @Test
    void najechanieZaznaczaOpcjeWOknie(@TempDir Path katalog) {
        Aplikacja a = poranek(katalog);
        a.obsluz(new Wejscie.Znak('b'));
        a.rysuj();
        int przed = wierszZaznaczenia(a);
        List<int[]> strefy = a.prostokatyStref();
        // Opcja tuż pod zaznaczoną (w budżecie opcje stoją w kolejnych wierszach).
        int[] ostatnia = strefy.stream().filter(s -> s[1] == przed + 1).findFirst().orElseThrow();
        a.obsluz(new Wejscie.Ruch(ostatnia[0] + 2, ostatnia[1]));
        a.rysuj();
        assertEquals(ostatnia[1], wierszZaznaczenia(a), "zaznaczenie idzie za kursorem");
        a.obsluz(new Wejscie.Kolko(-1, 0, 0));
        a.rysuj();
        assertNotEquals(ostatnia[1], wierszZaznaczenia(a), "kółko przesuwa zaznaczenie");
    }

    @Test
    void klikniecieWPrzyciskDzialaJakKlawisz(@TempDir Path katalog) {
        Aplikacja a = poranek(katalog);
        assertFalse(a.czyDialog());
        // Strefa przycisku „Budżet” na dolnym pasku: ta, której tekst zawiera "Budżet".
        String[] linie = a.klatka().tekst().split("\n");
        int[] budzet = a.prostokatyStref().stream()
                .filter(s -> linie[s[1] + 1].substring(0, Math.min(linie[s[1] + 1].length(), s[0] + s[2]))
                        .substring(Math.min(s[0], linie[s[1] + 1].length())).contains("Budżet"))
                .findFirst().orElseThrow();
        a.obsluz(new Wejscie.Ruch(budzet[0] + 1, budzet[1] + 1));
        a.rysuj();
        a.obsluz(new Wejscie.Klik(budzet[0] + 1, budzet[1] + 1));
        a.rysuj();
        assertTrue(a.czyDialog());
        assertTrue(a.klatka().tekst().contains("Budżet rekrutacji"));
    }

    @Test
    void kolkoPrzewijaDziennik(@TempDir Path katalog) {
        Aplikacja a = poranek(katalog);
        // Kilka dni, żeby dziennik był dłuższy niż jego okno.
        for (int i = 0; i < 400 && a.gra().getNumerDnia() < 4; i++) {
            a.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER));
            a.rysuj();
        }
        while (a.czyDialog()) {
            a.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ESC));
            a.rysuj();
        }
        String przed = a.klatka().tekst();
        a.obsluz(new Wejscie.Kolko(-1, 10, 25));
        a.rysuj();
        assertNotEquals(przed, a.klatka().tekst(), "kółko w górę pokazuje starsze wpisy");
        a.obsluz(new Wejscie.Kolko(1, 10, 25));
        a.rysuj();
        assertEquals(przed, a.klatka().tekst(), "kółko w dół wraca na koniec");
    }
}
