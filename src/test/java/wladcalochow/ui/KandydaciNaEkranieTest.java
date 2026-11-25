package wladcalochow.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wladcalochow.model.Potwor;
import wladcalochow.silnik.Gra;

/** Gracz widzi moc dzisiejszych kandydatów: w oknie potworów i w dzienniku. */
class KandydaciNaEkranieTest {

    private static Gra graZKandydatami() {
        for (long ziarno = 1; ; ziarno++) {
            Gra gra = Gra.nowa(ziarno);
            gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
            if (!gra.getDzien().getKandydaci().isEmpty()) {
                return gra;
            }
        }
    }

    @Test
    void oknoPotworowPokazujeKandydatowZMoca(@TempDir Path katalog) {
        Gra gra = graZKandydatami();
        Aplikacja a = new Aplikacja(new KonsolaBezEkranu(124, 36), katalog, 7L, katalog.resolve("claude"));
        a.pokazGre(gra, 0);
        a.rysuj();
        a.obsluz(new Wejscie.Znak('o'));
        a.rysuj();
        String ekran = a.klatka().tekst();
        assertTrue(ekran.contains("KANDYDACI DZIŚ"), ekran);
        for (Potwor k : gra.getDzien().getKandydaci()) {
            assertTrue(ekran.contains(k.getImie()), "kandydat " + k.getImie() + " na liście:\n" + ekran);
            assertTrue(ekran.contains(String.valueOf(Aplikacja.moc(k))), "moc kandydata " + k.getImie());
        }
        assertFalse(ekran.replace("… i jeszcze", "").contains("…"), ekran);
    }

    @Test
    void dziennikPodajeMocKandydatow() {
        Gra gra = graZKandydatami();
        Potwor k = gra.getDzien().getKandydaci().get(0);
        assertTrue(gra.getDziennik().stream().anyMatch(w -> w.tekst().contains(k.getImie() + " (")
                        && w.tekst().contains("moc " + Aplikacja.moc(k))),
                "w dzienniku jest moc kandydata " + k.getImie());
    }
}
