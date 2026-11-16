package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.Bohater;
import wladcalochow.model.Goblin;
import wladcalochow.model.Istota;
import wladcalochow.model.Lucznik;
import wladcalochow.model.Ogr;
import wladcalochow.model.Ork;
import wladcalochow.model.Potwor;
import wladcalochow.model.RundaWalki;
import wladcalochow.model.Rycerz;
import wladcalochow.model.Skarbiec;
import wladcalochow.model.Walka;
import wladcalochow.model.Zdarzenie;
import wladcalochow.model.Zlodziej;

class WalkaTest {

    @Test
    void strzelcySaWyciaganiJakoPierwsi() {
        for (long ziarno = 0; ziarno < 30; ziarno++) {
            List<Istota> uczestnicy = List.of(
                    new Rycerz("Sir", 15, 80, 90, 0), new Ork("Gruk", 14, 60, 70, 20),
                    new Lucznik("Wit", 18, 50, 60, 0), new Ogr("Kafar", 20, 100, 90, 40),
                    new Goblin("Smark", 7, 20, 40, 9), new Zlodziej("Cień", 40, 40, 5));
            RundaWalki runda = new RundaWalki(1, uczestnicy, new Random(ziarno));
            List<Istota> kolejnosc = new ArrayList<>();
            for (Istota i = runda.nastepny(); i != null; i = runda.nastepny()) {
                kolejnosc.add(i);
            }
            assertEquals(6, kolejnosc.size(), "każdy działa dokładnie raz");
            assertTrue(kolejnosc.get(0).atakujeNaOdleglosc() && kolejnosc.get(1).atakujeNaOdleglosc());
            assertTrue(kolejnosc.subList(2, 6).stream().noneMatch(Istota::atakujeNaOdleglosc));
            assertTrue(runda.czyZakonczona());
        }
    }

    private static List<Zdarzenie> rozegraj(Walka walka) {
        List<Zdarzenie> wszystkie = new ArrayList<>();
        int kroki = 0;
        while (!walka.czyZakonczona()) {
            wszystkie.addAll(walka.krok());
            assertTrue(++kroki < 100_000, "walka musi się skończyć");
        }
        return wszystkie;
    }

    @Test
    void walkaKonczySiePorazkaJednejZeStron() {
        for (long ziarno = 0; ziarno < 200; ziarno++) {
            List<Bohater> bohaterowie = List.of(new Rycerz("Sir", 15, 80, 90, 30),
                    new Lucznik("Wit", 18, 50, 60, 20), new Zlodziej("Cień", 40, 40, 5));
            List<Potwor> obroncy = List.of(new Goblin("Smark", 7, 20, 40, 9), new Ork("Gruk", 14, 60, 70, 20),
                    new Ogr("Kafar", 20, 100, 90, 40));
            Skarbiec skarbiec = new Skarbiec(500);
            Walka walka = new Walka(bohaterowie, obroncy, List.of(), skarbiec, 1.0, new Random(ziarno));
            rozegraj(walka);

            boolean bohaterowieWWalce = bohaterowie.stream().anyMatch(Istota::wWalce);
            boolean obroncyWWalce = obroncy.stream().anyMatch(Istota::wWalce);
            assertNotEquals(bohaterowieWWalce, obroncyWWalce, "dokładnie jedna strona zostaje na polu");
            assertEquals(obroncyWWalce ? Walka.Wynik.OBRONA_UDANA : Walka.Wynik.OBRONA_PRZEGRANA, walka.getWynik());
            assertTrue(skarbiec.getZloto() >= 0 && skarbiec.getZloto() <= 500);
            for (Istota istota : walka.uczestnicy()) {
                assertTrue(istota.getPunktyZycia() >= 0 && istota.getPunktyZycia() <= istota.getMaxPunktyZycia());
                assertTrue(istota.getMorale() >= 0 && istota.getMorale() <= istota.getMaxMorale());
            }
        }
    }

    @Test
    void toSamoZiarnoDajeTenSamPrzebieg() {
        List<String> pierwszy = opisy(42);
        assertEquals(pierwszy, opisy(42));
        assertNotEquals(pierwszy, opisy(43));
    }

    private static List<String> opisy(long ziarno) {
        Walka walka = new Walka(
                List.of(new Rycerz("Sir", 15, 80, 90, 30), new Lucznik("Wit", 18, 50, 60, 20)),
                List.of(new Goblin("Smark", 7, 20, 40, 9), new Ork("Gruk", 14, 60, 70, 20)),
                List.of(), new Skarbiec(100), 1.0, new Random(ziarno));
        return rozegraj(walka).stream().map(Zdarzenie::opis).toList();
    }

    @Test
    void zabojcaZabieraZlotoPoleglego() {
        Rycerz rycerz = new Rycerz("Sir", 1, 1, 90, 33);
        Ogr ogr = new Ogr("Kafar", 50, 500, 90, 40);
        Walka walka = new Walka(List.of(rycerz), List.of(ogr), List.of(), new Skarbiec(0), 1.0, new Random(1));
        List<Zdarzenie> zdarzenia = rozegraj(walka);
        assertFalse(rycerz.czyZyje());
        assertEquals(33, ogr.getZloto());
        assertEquals(33, ogr.getLupDnia());
        assertTrue(zdarzenia.stream().anyMatch(z -> z instanceof Zdarzenie.Smierc s && s.zloto() == 33));
        assertEquals(Walka.Wynik.OBRONA_UDANA, walka.getWynik());
    }

    @Test
    void ogluszonyTraciKolejke() {
        Rycerz rycerz = new Rycerz("Sir", 15, 1000, 1000, 0);
        Ork ork = new Ork("Gruk", 1, 1000, 1000, 20);
        Walka walka = new Walka(List.of(rycerz), List.of(ork), List.of(), new Skarbiec(0), 1.0, new Random(1));
        rycerz.oglusz();
        List<Zdarzenie> runda = new ArrayList<>();
        runda.addAll(walka.krok());
        runda.addAll(walka.krok());
        assertTrue(runda.stream().anyMatch(z -> z instanceof Zdarzenie.StrataKolejki s && s.kto() == rycerz));
        assertTrue(runda.stream().noneMatch(z -> z instanceof Zdarzenie.Atak a && a.kto() == rycerz));
        assertFalse(rycerz.czyOgluszona());
    }

    @Test
    void odwodWchodziDopieroPoUpadkuPierwszejLinii() {
        Rycerz rycerz = new Rycerz("Sir", 100, 2000, 2000, 0);
        Goblin pierwszaLinia = new Goblin("Smark", 1, 5, 400, 9);
        Ogr odwod = new Ogr("Kafar", 500, 3000, 90, 40);
        Walka walka = new Walka(List.of(rycerz), List.of(pierwszaLinia), List.of(odwod),
                new Skarbiec(0), 1.0, new Random(3));
        List<Zdarzenie> zdarzenia = rozegraj(walka);
        assertFalse(pierwszaLinia.czyZyje());
        assertEquals(Walka.Wynik.OBRONA_UDANA, walka.getWynik());
        assertTrue(walka.getOdwod().isEmpty());
        assertTrue(walka.getObroncy().contains(odwod));
        int smiercGoblina = indeks(zdarzenia, z -> z instanceof Zdarzenie.Smierc s && s.kto() == pierwszaLinia);
        int atakOgra = indeks(zdarzenia, z -> z instanceof Zdarzenie.Atak a && a.kto() == odwod);
        assertTrue(smiercGoblina >= 0 && atakOgra > smiercGoblina);
    }

    private static int indeks(List<Zdarzenie> zdarzenia, java.util.function.Predicate<Zdarzenie> warunek) {
        for (int i = 0; i < zdarzenia.size(); i++) {
            if (warunek.test(zdarzenia.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
