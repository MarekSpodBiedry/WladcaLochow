package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.Akcja;
import wladcalochow.model.Goblin;
import wladcalochow.model.Istota;
import wladcalochow.model.Lucznik;
import wladcalochow.model.Ogr;
import wladcalochow.model.Ork;
import wladcalochow.model.PoleWalki;
import wladcalochow.model.Rycerz;
import wladcalochow.model.Zlodziej;

class IstotyTest {

    /** Pole walki z ustaloną listą przeciwników. */
    private static PoleWalki pole(Istota... przeciwnicy) {
        return new PoleWalki() {
            @Override
            public List<Istota> przeciwnicy(Istota kto) {
                return List.of(przeciwnicy);
            }

            @Override
            public List<Istota> sojusznicy(Istota kto) {
                return List.of(kto);
            }
        };
    }

    @Test
    void rycerzUciekaDopieroPonizejPieciuProcentZycia() {
        Rycerz rycerz = new Rycerz("Sir Test", 15, 100, 1000, 10);
        rycerz.otrzymajAtak(95);
        assertFalse(rycerz.czyUcieka(pole()), "5% życia to jeszcze nie ucieczka");
        rycerz.otrzymajAtak(1);
        assertTrue(rycerz.czyUcieka(pole()));
    }

    @Test
    void rycerzUciekaGdyMoraleSpadnieDoZera() {
        Rycerz rycerz = new Rycerz("Sir Test", 15, 1000, Istota.SPADEK_MORALE_PO_TRAFIENIU, 10);
        assertFalse(rycerz.czyUcieka(pole()));
        rycerz.otrzymajAtak(1);
        assertEquals(0, rycerz.getMorale());
        assertTrue(rycerz.czyUcieka(pole()));
    }

    @Test
    void orkUciekaPonizejDwudziestuProcentZycia() {
        Ork ork = new Ork("Gruk", 14, 100, 1000, 20);
        ork.otrzymajAtak(80);
        assertFalse(ork.czyUcieka(pole()));
        ork.otrzymajAtak(1);
        assertTrue(ork.czyUcieka(pole()));
    }

    @Test
    void ogrNieTraciMoraleWWalceIBijeTenSamCel() {
        Ogr ogr = new Ogr("Kafar", 20, 100, 90, 40);
        ogr.otrzymajAtak(30);
        assertEquals(90, ogr.getMorale());
        assertEquals(70, ogr.getPunktyZycia());

        Rycerz pierwszy = new Rycerz("A", 10, 50, 50, 0);
        Rycerz drugi = new Rycerz("B", 10, 50, 50, 0);
        Random los = new Random(7);
        Istota cel = ((Akcja.Atak) ogr.wybierzAkcje(pole(pierwszy, drugi), los)).cel();
        for (int i = 0; i < 20; i++) {
            assertSame(cel, ((Akcja.Atak) ogr.wybierzAkcje(pole(pierwszy, drugi), los)).cel());
        }
        Istota inny = cel == pierwszy ? drugi : pierwszy;
        assertSame(inny, ((Akcja.Atak) ogr.wybierzAkcje(pole(inny), los)).cel(),
                "gdy cel zniknie, ogr wybiera następnego");
    }

    @Test
    void zlodziejNieAtakujeIUciekaPrzedSilnymPotworem() {
        Zlodziej zlodziej = new Zlodziej("Cień", 50, 40, 5);
        assertEquals(0, zlodziej.getSilaAtaku());
        Goblin slaby = new Goblin("Smark", 9, 20, 30, 8);
        Ork silny = new Ork("Gruk", 10, 60, 70, 20);
        assertFalse(zlodziej.czyUcieka(pole(slaby)), "siła 9 to mniej niż 20% z 50 życia");
        assertTrue(zlodziej.czyUcieka(pole(slaby, silny)), "siła 10 to już 20% z 50 życia");
        Akcja akcja = zlodziej.wybierzAkcje(pole(slaby), new Random(1));
        assertEquals(new Akcja.ProbaKradziezy(0.20, false), akcja);
    }

    @Test
    void lucznikPrzyWysokimMoraleCelujeWNajslabszegoStrzelca() {
        Lucznik lucznik = new Lucznik("Wit", 18, 50, 60, 10);
        Ork ranny = new Ork("Ranny", 14, 60, 70, 20);
        ranny.otrzymajAtak(55);
        Goblin zdrowy = new Goblin("Zdrowy", 7, 26, 40, 9);
        Goblin slabszy = new Goblin("Slabszy", 7, 20, 40, 9);
        Akcja akcja = lucznik.wybierzAkcje(pole(ranny, zdrowy, slabszy), new Random(3));
        assertSame(slabszy, ((Akcja.Atak) akcja).cel(), "strzelcy mają pierwszeństwo przed rannym orkiem");

        Akcja bezStrzelcow = lucznik.wybierzAkcje(pole(ranny, new Ork("Caly", 14, 60, 70, 20)), new Random(3));
        assertSame(ranny, ((Akcja.Atak) bezStrzelcow).cel());
    }

    @Test
    void lucznikPrzyNiskimMoraleNadalWybieraWsrodStrzelcow() {
        Lucznik lucznik = new Lucznik("Wit", 18, 500, 100, 10);
        lucznik.zmienMorale(-60);
        Ork ork = new Ork("Gruk", 14, 60, 70, 20);
        Goblin goblin = new Goblin("Smark", 7, 20, 40, 9);
        Random los = new Random(11);
        for (int i = 0; i < 30; i++) {
            assertSame(goblin, ((Akcja.Atak) lucznik.wybierzAkcje(pole(ork, goblin), los)).cel());
        }
    }

    @Test
    void ciezkoRannyLucznikProbujeKrascIUcieka() {
        Lucznik lucznik = new Lucznik("Wit", 18, 100, 1000, 10);
        lucznik.otrzymajAtak(81);
        Akcja akcja = lucznik.wybierzAkcje(pole(new Ork("Gruk", 14, 60, 70, 20)), new Random(1));
        assertEquals(new Akcja.ProbaKradziezy(0.10, true), akcja);
    }

    @Test
    void strzelcyAtakujaNaOdlegloscAPozostaliNie() {
        assertTrue(new Lucznik("Wit", 18, 50, 60, 10).atakujeNaOdleglosc());
        assertTrue(new Goblin("Smark", 7, 20, 40, 9).atakujeNaOdleglosc());
        assertFalse(new Rycerz("Sir", 15, 80, 90, 10).atakujeNaOdleglosc());
        assertFalse(new Ork("Gruk", 14, 60, 70, 20).atakujeNaOdleglosc());
        assertFalse(new Ogr("Kafar", 20, 100, 90, 40).atakujeNaOdleglosc());
        assertFalse(new Zlodziej("Cień", 40, 40, 5).atakujeNaOdleglosc());
    }

    @Test
    void zabojcaZabieraZlotoAleZoldNalezySieCaly() {
        Ork ork = new Ork("Gruk", 14, 60, 70, 20);
        Rycerz rycerz = new Rycerz("Sir", 15, 80, 90, 12);
        assertEquals(12, ork.zabierzZloto(rycerz));
        assertEquals(0, rycerz.getZloto());
        assertEquals(12, ork.getZloto());
        assertEquals(20, ork.nalezneDzis(), "łup trafia do skarbca, więc nie pomniejsza żołdu");
        assertEquals(12, ork.oddajZloto());
        assertEquals(0, ork.getZloto());
    }

    @Test
    void domyslnieIstotaAtakujeLosowegoPrzeciwnika() {
        Ork ork = new Ork("Gruk", 14, 60, 70, 20);
        Rycerz a = new Rycerz("A", 15, 80, 90, 0);
        Rycerz b = new Rycerz("B", 15, 80, 90, 0);
        Random los = new Random(5);
        boolean trafilA = false;
        boolean trafilB = false;
        for (int i = 0; i < 50; i++) {
            Istota cel = ((Akcja.Atak) ork.wybierzAkcje(pole(a, b), los)).cel();
            trafilA |= cel == a;
            trafilB |= cel == b;
        }
        assertTrue(trafilA && trafilB);
        assertInstanceOf(Akcja.Czekanie.class, ork.wybierzAkcje(pole(), los));
    }
}
