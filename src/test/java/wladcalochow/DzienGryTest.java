package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.Bohater;
import wladcalochow.model.BylejakiMinisterPracy;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Goblin;
import wladcalochow.model.Loch;
import wladcalochow.model.Ogr;
import wladcalochow.model.Ork;
import wladcalochow.model.Potwor;
import wladcalochow.model.RamboMinisterPracy;
import wladcalochow.model.RozrzutnyMinisterFinansow;
import wladcalochow.model.Rycerz;
import wladcalochow.model.Skarbiec;
import wladcalochow.model.Walka;
import wladcalochow.model.Zdarzenie;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.RaportZwiadu;
import wladcalochow.model.strategia.Ulepszenie;

class DzienGryTest {

    private static Loch loch(int zloto) {
        Loch loch = new Loch(new Skarbiec(zloto));
        loch.setMinisterFinansow(new RozrzutnyMinisterFinansow("Finanse", 0, 0));
        loch.setMinisterPracy(new RamboMinisterPracy("Kadry", 0, 0, 0));
        return loch;
    }

    private static DzienGry dzien(int dochod, List<Potwor> kandydaci, List<Bohater> bohaterowie) {
        return dzien(dochod, kandydaci, bohaterowie, 0);
    }

    private static DzienGry dzien(int dochod, List<Potwor> kandydaci, List<Bohater> bohaterowie, int zlotoRano) {
        return new DzienGry(1, dochod, kandydaci, bohaterowie, List.of(), List.of(),
                new RaportZwiadu(0, 0, null, 0), zlotoRano);
    }

    @Test
    void bilansDniaZgadzaSieZeSkarbcem() {
        Loch loch = loch(500);
        loch.setMinisterFinansow(new RozrzutnyMinisterFinansow("Finanse", 12, 0));
        loch.setMinisterPracy(new BylejakiMinisterPracy("Kadry", 8, 0, 0));
        loch.zatrudnij(new Ork("A", 14, 60, 70, 20));
        loch.zatrudnij(new Ork("B", 14, 60, 70, 25));
        loch.zatrudnij(new Goblin("C", 7, 20, 40, 9));
        loch.dodajWydobycie(33);
        DzienGry dzien = dzien(40, List.of(new Goblin("Nowy", 7, 20, 40, 11)), List.of(), 500);
        dzien.zuzyjRozkaz(0);
        dzien.rozegrajPoranek(loch, new Random(4));
        dzien.przygotujObrone(loch, new Random(4), new ArrayList<>());
        dzien.rozegrajWieczor(loch, new Random(4));

        var bilans = dzien.getBilans();
        assertEquals(500, bilans.getZlotoRano());
        assertEquals(40, bilans.getDochod());
        assertEquals(33, bilans.getKopalnia());
        // Pensje: 12 + 8 stałej części plus po 3% z przychodu 73 (po 2 złota).
        assertEquals(12 + 8 + 2 * Math.round(73 * 0.03), bilans.getPensje());
        assertEquals(11, bilans.getRekrutacja());
        assertEquals(List.of("Nowy"), bilans.getZatrudnieni());
        assertEquals(wladcalochow.model.BilansDnia.Obrona.SPOKOJ, bilans.getObrona());
        assertEquals(loch.getSkarbiec().getZloto(), bilans.getZlotoWieczorem());
        assertEquals(bilans.getZlotoRano() + bilans.przychody() - bilans.wydatki(), bilans.getZlotoWieczorem(),
                "złoto rano + przychody - wydatki = złoto wieczorem");
        assertEquals(loch.getWydobycieDoOdebrania(), bilans.getWydobycieNaJutro());
    }

    private static void rozegrajWalke(Walka walka) {
        while (!walka.czyZakonczona()) {
            walka.krok();
        }
    }

    @Test
    void poranekPrzynosiDochodIZatrudniaWGranicachBudzetu() {
        Loch loch = loch(100);
        loch.zatrudnij(new Ork("Stary", 14, 60, 70, 30));
        Ogr drogi = new Ogr("Drogi", 20, 100, 90, 200);
        Goblin tani = new Goblin("Tani", 7, 20, 40, 10);
        DzienGry dzien = dzien(50, List.of(drogi, tani), List.of());
        dzien.rozegrajPoranek(loch, new Random(1));
        // 150 złota, żołd załogi 30, więc budżet 120: Rambo chce najpierw ogra za 200 i rezygnuje.
        assertEquals(150, loch.getSkarbiec().getZloto());
        assertEquals(1, loch.getListaPotworow().size());

        loch.setMinisterPracy(new BylejakiMinisterPracy("Byle", 0, 0, 0));
        DzienGry drugi = dzien(0, List.of(drogi, tani), List.of());
        drugi.rozegrajPoranek(loch, new Random(1));
        assertEquals(140, loch.getSkarbiec().getZloto(), "Bylejaki pomija ogra i bierze goblina za 10");
        assertTrue(loch.getListaPotworow().contains(tani));
    }

    @Test
    void limitRekrutacjiOgraniczaBudzet() {
        Loch loch = loch(100);
        loch.setLimitRekrutacji(0);
        Goblin tani = new Goblin("Tani", 7, 20, 40, 10);
        dzien(0, List.of(tani), List.of()).rozegrajPoranek(loch, new Random(1));
        assertTrue(loch.getListaPotworow().isEmpty());
        assertEquals(100, loch.getSkarbiec().getZloto());
    }

    @Test
    void lochNiePrzyjmujeWiecejPotworowNizMaMiejsc() {
        Loch loch = loch(10_000);
        loch.setMinisterPracy(new BylejakiMinisterPracy("Byle", 0, 0, 0));
        for (int i = 0; i < Loch.POJEMNOSC - 1; i++) {
            loch.zatrudnij(new Goblin("G" + i, 7, 20, 40, 1));
        }
        List<Potwor> kandydaci = List.of(new Goblin("A", 7, 20, 40, 5), new Goblin("B", 7, 20, 40, 5),
                new Goblin("C", 7, 20, 40, 5));
        int zlotoPrzed = loch.getSkarbiec().getZloto();
        dzien(0, kandydaci, List.of()).rozegrajPoranek(loch, new Random(1));
        assertEquals(Loch.POJEMNOSC, loch.getListaPotworow().size());
        assertEquals(zlotoPrzed - 5, loch.getSkarbiec().getZloto(), "zapłacono tylko za jednego przyjętego");
    }

    @Test
    void gornicyNieWalczaINieDostajaZolduAWydobycieWplywaNastepnegoDnia() {
        Loch loch = loch(1000);
        Goblin slaby = new Goblin("Slaby", 6, 20, 40, 10);
        Ork sredni = new Ork("Sredni", 14, 60, 70, 20);
        Ogr silny = new Ogr("Silny", 20, 100, 90, 40);
        List.of(slaby, sredni, silny).forEach(loch::zatrudnij);
        slaby.zmienMorale(-20);
        DzienGry dzien = dzien(0, List.of(), List.of());
        dzien.rozegrajPoranek(loch, new Random(1));
        assertEquals(List.of(slaby), loch.gornicy(), "Rambo wysyła do kopalni najsłabszego");
        assertNull(dzien.przygotujObrone(loch, new Random(1), new ArrayList<>()));
        dzien.rozegrajWieczor(loch, new Random(1));
        assertEquals(1000 - 60, loch.getSkarbiec().getZloto(), "żołd dostają tylko ork i ogr");
        assertEquals(13, loch.getWydobycieDoOdebrania(), "(6 + 20) * 50% morale");
        assertEquals(20, slaby.getMorale(), "górnik nie dostaje żołdu, więc morale mu nie rośnie");
        assertFalse(slaby.czyWKopalni());

        dzien(0, List.of(), List.of()).rozegrajPoranek(loch, new Random(1));
        assertEquals(1000 - 60 + 13, loch.getSkarbiec().getZloto());
    }

    @Test
    void przekupstwoOdsylaBohaterowBezWalki() {
        Loch loch = loch(1000);
        loch.zatrudnij(new Ork("Gruk", 14, 60, 70, 20));
        loch.setPostawa(Postawa.PRZEKUPSTWO);
        Rycerz rycerz = new Rycerz("Sir", 16, 80, 90, 10);
        DzienGry dzien = dzien(0, List.of(), List.of(rycerz));
        dzien.rozegrajPoranek(loch, new Random(1));
        int cena = dzien.kosztPrzekupstwa(loch);
        assertEquals(Math.round((2 * 16 + 80 / 2) * rycerz.mnoznikWartosci()), cena, "wartość rycerza z mnożnikiem rodzaju");
        assertNull(dzien.przygotujObrone(loch, new Random(1), new ArrayList<>()));
        assertEquals(1000 - cena, loch.getSkarbiec().getZloto());
        assertEquals(0, loch.getPorazkiZRzedu());
    }

    @Test
    void gdyNaLapowkeNieStacLochBroniSieNormalnie() {
        Loch loch = loch(5);
        loch.zatrudnij(new Ork("Gruk", 14, 60, 70, 0));
        loch.setPostawa(Postawa.PRZEKUPSTWO);
        DzienGry dzien = dzien(0, List.of(), List.of(new Rycerz("Sir", 16, 80, 90, 10)));
        dzien.rozegrajPoranek(loch, new Random(1));
        assertNotNull(dzien.przygotujObrone(loch, new Random(1), new ArrayList<>()));
        assertEquals(5, loch.getSkarbiec().getZloto());
    }

    @Test
    void pulapkiRaniaBohaterowPrzedWalka() {
        Loch loch = loch(1000);
        loch.zatrudnij(new Ork("Gruk", 14, 60, 70, 20));
        loch.ulepsz(Ulepszenie.PULAPKI);
        loch.ulepsz(Ulepszenie.PULAPKI);
        Rycerz rycerz = new Rycerz("Sir", 16, 100, 90, 10);
        DzienGry dzien = dzien(0, List.of(), List.of(rycerz));
        dzien.rozegrajPoranek(loch, new Random(1));
        dzien.przygotujObrone(loch, new Random(1), new ArrayList<>());
        assertEquals(80, rycerz.getPunktyZycia());
        assertEquals(90, rycerz.getMorale(), "pułapka nie obniża morale");
    }

    @Test
    void odwodZatrzymujeTrzeciaNajcenniejszychWojownikow() {
        Loch loch = loch(1000);
        // Bylejaki minister wysyła do kopalni losowo, więc w tym teście kopalnię omijamy: 3 potwory to 1 górnik.
        List<Ork> orki = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Ork ork = new Ork("Ork" + i, 10 + i, 60, 100, 20);
            orki.add(ork);
            loch.zatrudnij(ork);
        }
        loch.setPostawa(Postawa.ODWOD);
        DzienGry dzien = dzien(0, List.of(), List.of(new Rycerz("Sir", 16, 80, 90, 10)));
        dzien.rozegrajPoranek(loch, new Random(1));
        Walka walka = dzien.przygotujObrone(loch, new Random(1), new ArrayList<>());
        List<Potwor> wojownicy = new ArrayList<>(walka.getObroncy());
        wojownicy.addAll(walka.getOdwod());
        assertEquals(wojownicy.size() / 3, walka.getOdwod().size(), "w odwodzie czeka jedna trzecia wojowników");
        int najslabszyWOdwodzie = walka.getOdwod().stream().mapToInt(Potwor::moc).min().orElseThrow();
        int najsilniejszyWLinii = walka.getObroncy().stream().mapToInt(Potwor::moc).max().orElseThrow();
        assertTrue(najslabszyWOdwodzie >= najsilniejszyWLinii, "w odwodzie są najcenniejsi");
    }

    @Test
    void niebronionyLochTraciCalySkarbiec() {
        Loch loch = loch(400);
        DzienGry dzien = dzien(0, List.of(), List.of(new Rycerz("Sir", 16, 80, 90, 10)));
        dzien.rozegrajPoranek(loch, new Random(1));
        assertNull(dzien.przygotujObrone(loch, new Random(1), new ArrayList<>()));
        assertEquals(0, loch.getSkarbiec().getZloto());
        assertEquals(1, loch.getPorazkiZRzedu());
    }

    @Test
    void przegranaObronaToPladrowanieAUdanaZerujeLicznikPorazek() {
        Loch loch = loch(1000);
        Goblin ofiara = new Goblin("Ofiara", 1, 1, 40, 0);
        loch.zatrudnij(ofiara);
        DzienGry dzien = dzien(0, List.of(), List.of(new Rycerz("Sir", 500, 5000, 5000, 10)));
        dzien.rozegrajPoranek(loch, new Random(2));
        rozegrajWalke(dzien.przygotujObrone(loch, new Random(2), new ArrayList<>()));
        List<Zdarzenie> skutki = dzien.zakonczObrone(loch, new Random(2));
        assertTrue(loch.getSkarbiec().getZloto() < 1000 && loch.getSkarbiec().getZloto() >= 600,
                "jeden zwycięzca wynosi 20-35% skarbca");
        assertFalse(loch.getListaPotworow().contains(ofiara), "polegli znikają z lochu");
        assertEquals(1, loch.getPorazkiZRzedu());
        assertFalse(skutki.isEmpty());

        loch.zanotujWynikObrony(false);
        assertFalse(loch.czyZdobyty());
        loch.zanotujWynikObrony(false);
        assertTrue(loch.czyZdobyty(), "trzy porażki z rzędu to upadek lochu");
        loch.zanotujWynikObrony(true);
        assertEquals(0, loch.getPorazkiZRzedu());
    }

    @Test
    void uciekinierWracaDoLochuPoWalce() {
        Loch loch = loch(1000);
        Ork tchorz = new Ork("Tchorz", 1, 1000, 1, 20);
        Ogr mocarz = new Ogr("Mocarz", 900, 9000, 90, 40);
        loch.zatrudnij(tchorz);
        loch.zatrudnij(mocarz);
        DzienGry dzien = dzien(0, List.of(), List.of(new Rycerz("Sir", 1, 1500, 5000, 10),
                new Rycerz("Sir Drugi", 1, 1500, 5000, 10)));
        dzien.rozegrajPoranek(loch, new Random(3));
        Walka walka = dzien.przygotujObrone(loch, new Random(3), new ArrayList<>());
        tchorz.zmienMorale(-1);
        rozegrajWalke(walka);
        assertTrue(tchorz.czyUciekla(), "ork z zerowym morale ucieka z pola walki");
        dzien.zakonczObrone(loch, new Random(3));
        assertTrue(loch.getListaPotworow().contains(tchorz), "uciekinier zostaje w załodze");
        assertFalse(tchorz.czyUciekla(), "po walce nie jest już oznaczony jako uciekinier");
        assertTrue(tchorz.wWalce());
    }

    @Test
    void weteranAwansujePoPrzezytychWalkach() {
        Ork ork = new Ork("Gruk", 10, 100, 70, 20);
        assertFalse(ork.podsumujWalke());
        assertFalse(ork.podsumujWalke());
        assertTrue(ork.podsumujWalke(), "trzecia przeżyta walka daje poziom 2");
        assertEquals(2, ork.getPoziom());
        assertEquals(11, ork.getSilaAtaku());
        assertEquals(110, ork.getMaxPunktyZycia());
        assertEquals(22, ork.getZold());
    }

    @Test
    void ministerBezPensjiOdchodziIZastepujeGoStazysta() {
        Loch loch = loch(5);
        loch.setMinisterFinansow(new RozrzutnyMinisterFinansow("Drogi", 50, 0.2));
        dzien(0, List.of(), List.of()).rozegrajPoranek(loch, new Random(1));
        assertEquals(0, loch.getMinisterFinansow().getPensja());
        assertEquals(0.0, loch.getMinisterFinansow().getNegocjacje());
    }
}
