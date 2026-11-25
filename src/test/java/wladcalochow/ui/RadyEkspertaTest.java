package wladcalochow.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wladcalochow.model.Loch;
import wladcalochow.model.Ogr;
import wladcalochow.silnik.Ekspert;
import wladcalochow.silnik.FabrykaIstot;
import wladcalochow.silnik.Gra;

/** Panel „Co zrobiłby ekspert”: włączany klawiszem E, pokazuje rady z silnik.Ekspert i mieści się w oknie. */
class RadyEkspertaTest {

    private static Aplikacja poranek(Path katalog, int szer, int wys, Gra gra) {
        Aplikacja a = new Aplikacja(new KonsolaBezEkranu(szer, wys), katalog, 7L, katalog.resolve("claude"));
        a.pokazGre(gra, 0);
        a.rysuj();
        return a;
    }

    private static Gra nowaGra(long ziarno) {
        Gra gra = Gra.nowa(ziarno);
        gra.rozpocznij(Ekspert.startowyFinansow(gra.getStartowiFinansow()), Ekspert.startowyPracy(gra.getStartowiPracy()));
        return gra;
    }

    @Test
    void klawiszEWlaczaIWylaczaPanel(@TempDir Path katalog) {
        Aplikacja a = poranek(katalog, 124, 36, nowaGra(7));
        assertFalse(a.klatka().tekst().contains("Co zrobiłby ekspert"), "domyślnie gracz kombinuje sam");
        a.obsluz(new Wejscie.Znak('e'));
        a.rysuj();
        String ekran = a.klatka().tekst();
        assertTrue(ekran.contains("Co zrobiłby ekspert"), ekran);
        a.obsluz(new Wejscie.Znak('e'));
        a.rysuj();
        assertFalse(a.klatka().tekst().contains("Co zrobiłby ekspert"));
    }

    @Test
    void panelPokazujePierwszaRadeEksperta(@TempDir Path katalog) {
        Gra gra = nowaGra(7);
        gra.getLoch().getSkarbiec().wplac(2000);  // stać na ulepszenie, więc ekspert ma co radzić
        Aplikacja a = poranek(katalog, 124, 36, gra);
        a.obsluz(new Wejscie.Znak('e'));
        a.rysuj();
        assertFalse(Ekspert.rady(gra).isEmpty());
        String powod = Ekspert.rady(gra).get(0).powod();
        assertTrue(a.klatka().tekst().contains(powod), "w panelu jest powód pierwszej rady: " + powod);
    }

    @Test
    void pelnyLochZRadamiMiesciSieWNajmniejszymOknie(@TempDir Path katalog) {
        for (long ziarno = 1; ziarno <= 6; ziarno++) {
            Gra gra = nowaGra(ziarno);
            Loch loch = gra.getLoch();
            loch.getSkarbiec().wplac(900_000);
            String dlugie = "W".repeat(FabrykaIstot.MAX_DLUGOSC_IMIENIA - 1);
            for (int i = 0; loch.wolneMiejsca() > 0; i++) {
                Ogr ogr = new Ogr(dlugie + (char) ('a' + i), 60, 900, 100, 45);
                ogr.zmienMorale(-90);  // niskie morale: ekspert radzi premię z długim imieniem
                loch.zatrudnij(ogr);
            }
            Aplikacja a = poranek(katalog.resolve("z" + ziarno), Aplikacja.MIN_SZER, Aplikacja.MIN_WYS, gra);
            a.obsluz(new Wejscie.Znak('e'));
            a.rysuj();
            String ekran = a.klatka().tekst();
            assertTrue(ekran.contains("Co zrobiłby ekspert"), ekran);
            // „… i jeszcze N” to celowy skrót listy potworów, a nie przycięty tekst.
            assertFalse(ekran.replace("… i jeszcze", "").contains("…"), "nic nie jest przycięte:\n" + ekran);
        }
    }
}
