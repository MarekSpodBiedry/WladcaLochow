package wladcalochow.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wladcalochow.model.Potwor;
import wladcalochow.silnik.Gra;

/** Ten sam przycisk ma zawsze ten sam numer: gracz klika z przyzwyczajenia. */
class StaleNumeryOpcjiTest {

    private static Aplikacja poranek(Path katalog) {
        Aplikacja a = new Aplikacja(new KonsolaBezEkranu(124, 36), katalog, 7L, katalog.resolve("claude"));
        Gra gra = Gra.nowa(7);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        a.pokazGre(gra, 0);
        a.rysuj();
        return a;
    }

    /** Otwiera okno pierwszego potwora z listy i zwraca tekst ekranu. */
    private static String oknoPierwszegoPotwora(Aplikacja a) {
        a.obsluz(new Wejscie.Znak('o'));
        a.rysuj();
        a.obsluz(new Wejscie.Znak('1'));
        a.rysuj();
        return a.klatka().tekst();
    }

    private static boolean opcja(String ekran, int numer, String nazwa) {
        return ekran.lines().anyMatch(l -> l.matches(".*\\b" + numer + "\\s+" + nazwa + "(\\s.*|$)"));
    }

    @Test
    void wOknieZdrowegoIRannegoPotworaNumeryOpcjiSaTeSame(@TempDir Path katalog) {
        Aplikacja zdrowy = poranek(katalog.resolve("a"));
        String ekranZdrowego = oknoPierwszegoPotwora(zdrowy);
        assertTrue(opcja(ekranZdrowego, 3, "Szpital"), "zdrowy też widzi przygaszony szpital:\n" + ekranZdrowego);
        assertTrue(opcja(ekranZdrowego, 4, "Wróć"), ekranZdrowego);

        Aplikacja ranny = poranek(katalog.resolve("b"));
        for (Potwor p : ranny.gra().getLoch().getListaPotworow()) {
            p.zranCiezko(2);
        }
        String ekranRannego = oknoPierwszegoPotwora(ranny);
        assertTrue(opcja(ekranRannego, 3, "Szpital"), ekranRannego);
        assertTrue(opcja(ekranRannego, 4, "Wróć"), ekranRannego);
    }
}
