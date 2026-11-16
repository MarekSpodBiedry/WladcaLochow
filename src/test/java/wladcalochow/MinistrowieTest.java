package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.BylejakiMinisterPracy;
import wladcalochow.model.Goblin;
import wladcalochow.model.MinisterFinansow;
import wladcalochow.model.MinisterPracy;
import wladcalochow.model.Ogr;
import wladcalochow.model.Ork;
import wladcalochow.model.Potwor;
import wladcalochow.model.RamboMinisterPracy;
import wladcalochow.model.RozrzutnyMinisterFinansow;
import wladcalochow.model.Rycerz;
import wladcalochow.model.Skarbiec;
import wladcalochow.model.ZachowawczyMinisterFinansow;

class MinistrowieTest {

    private final Goblin goblin = new Goblin("Smark", 7, 20, 40, 10);
    private final Ork ork = new Ork("Gruk", 14, 60, 70, 25);
    private final Ogr ogr = new Ogr("Kafar", 20, 100, 90, 40);

    @Test
    void rozrzutnyZostawiaTylkoNaDzisiejszyZold() {
        MinisterFinansow minister = new RozrzutnyMinisterFinansow("R", 10, 0);
        assertEquals(380, minister.kwotaNaRekrutacje(500, 120));
        assertEquals(0, minister.kwotaNaRekrutacje(100, 120));
    }

    @Test
    void zachowawczyTrzymaRezerweNaTrzyDni() {
        MinisterFinansow minister = new ZachowawczyMinisterFinansow("Z", 10, 0);
        assertEquals(140, minister.kwotaNaRekrutacje(500, 120));
        assertEquals(0, minister.kwotaNaRekrutacje(300, 120));
    }

    @Test
    void negocjacjeObnizajaCeny() {
        assertEquals(80, new RozrzutnyMinisterFinansow("R", 10, 0.20).cenaPoNegocjacjach(100));
        assertEquals(100, new RozrzutnyMinisterFinansow("R", 10, 0).cenaPoNegocjacjach(100));
    }

    @Test
    void ramboBierzeNajsilniejszychIRezygnujeGdyGoNieStac() {
        MinisterPracy rambo = new RamboMinisterPracy("Rambo", 10, 0, 0);
        // Ogr (40) mieści się w budżecie 60, ork (25) już nie, więc Rambo rezygnuje mimo taniego goblina.
        assertEquals(List.of(ogr), rambo.wybierzDoZatrudnienia(List.of(goblin, ork, ogr), 60));
        assertEquals(List.of(ogr, ork, goblin), rambo.wybierzDoZatrudnienia(List.of(goblin, ork, ogr), 75));
        assertEquals(List.of(), rambo.wybierzDoZatrudnienia(List.of(goblin, ork, ogr), 39));
    }

    @Test
    void bylejakiBierzeJakLeciIPomijaZbytDrogich() {
        MinisterPracy bylejaki = new BylejakiMinisterPracy("Byle", 10, 0, 0);
        assertEquals(List.of(ork, goblin), bylejaki.wybierzDoZatrudnienia(List.of(ork, ogr, goblin), 60));
        assertEquals(List.of(goblin), bylejaki.wybierzDoZatrudnienia(List.of(ogr, ork, goblin), 12));
    }

    @Test
    void doKopalniIdzieJednaTrzeciaZalogiARamboWysylaNajslabszych() {
        Goblin drugi = new Goblin("Glut", 6, 18, 30, 8);
        Ork trzeci = new Ork("Morg", 12, 50, 60, 20);
        Ogr czwarty = new Ogr("Kloc", 18, 90, 80, 35);
        List<Potwor> stan = List.of(ogr, ork, goblin, drugi, trzeci, czwarty);

        List<Potwor> odRambo = new RamboMinisterPracy("Rambo", 10, 0, 0).wybierzDoKopalni(stan, new Random(1));
        assertEquals(List.of(drugi, goblin), odRambo);

        List<Potwor> odBylejakiego = new BylejakiMinisterPracy("Byle", 10, 0, 0).wybierzDoKopalni(stan, new Random(1));
        assertEquals(2, odBylejakiego.size());
        assertTrue(stan.containsAll(odBylejakiego));

        assertEquals(0, new RamboMinisterPracy("Rambo", 10, 0, 0).wybierzDoKopalni(List.of(ogr, ork), new Random(1)).size());
    }

    @Test
    void zoldPodnosiMoraleABrakZolduJeObniza() {
        goblin.zmienMorale(-20);
        Skarbiec skarbiec = new Skarbiec(10);
        MinisterFinansow.WynikWyplaty wynik = new RozrzutnyMinisterFinansow("R", 0, 0)
                .wyplacZold(skarbiec, List.of(goblin), new Random(1));
        assertEquals(List.of(goblin), wynik.oplaceni());
        assertEquals(10, wynik.wyplacono());
        assertEquals(0, skarbiec.getZloto());
        assertEquals(28, goblin.getMorale(), "+20% z 40");

        wynik = new RozrzutnyMinisterFinansow("R", 0, 0).wyplacZold(skarbiec, List.of(goblin), new Random(1));
        assertEquals(List.of(goblin), wynik.nieoplaceni());
        assertEquals(24, goblin.getMorale(), "-10% z 40");
    }

    @Test
    void nieMaCzesciowegoZolduAPotworZFatalnymMoraleOdchodzi() {
        ork.zmienMorale(-65);
        Skarbiec skarbiec = new Skarbiec(24);
        MinisterFinansow.WynikWyplaty wynik = new ZachowawczyMinisterFinansow("Z", 0, 0)
                .wyplacZold(skarbiec, List.of(ork), new Random(1));
        assertEquals(24, skarbiec.getZloto(), "żołd 25 przy 24 złota: nie ma wypłaty częściowej");
        assertEquals(List.of(ork), wynik.odeszli(), "morale 5 z 70 to mniej niż 10%");
    }

    @Test
    void lupZWalkNiePomniejszaWyplatyZoldu() {
        ork.zabierzZloto(new Rycerz("Sir", 15, 80, 90, 15));
        Skarbiec skarbiec = new Skarbiec(100);
        new RozrzutnyMinisterFinansow("R", 0, 0).wyplacZold(skarbiec, List.of(ork), new Random(1));
        assertEquals(75, skarbiec.getZloto(), "pełny żołd 25: łup idzie do skarbca, a nie zamiast żołdu");
    }
}
