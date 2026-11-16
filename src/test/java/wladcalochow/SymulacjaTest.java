package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Loch;
import wladcalochow.model.Potwor;
import wladcalochow.model.Walka;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.model.strategia.Ulepszenie;
import wladcalochow.silnik.Gra;
import wladcalochow.silnik.ZapisGry;

class SymulacjaTest {

    private static Gra nowaGra(long ziarno) {
        Gra gra = Gra.nowa(ziarno);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        return gra;
    }

    /** Gracz-automat: wydaje losowe, dozwolone rozkazy. */
    private static void wydajLosoweRozkazy(Gra gra, Random bot) {
        Loch loch = gra.getLoch();
        DzienGry dzien = gra.getDzien();
        for (int proba = 0; proba < 6 && dzien.maRozkazy(); proba++) {
            List<Rozkaz> pomysly = new ArrayList<>();
            pomysly.add(new Rozkaz.ZmianaPostawy(Postawa.values()[bot.nextInt(Postawa.values().length)]));
            pomysly.add(new Rozkaz.LimitRekrutacji(25 * bot.nextInt(5)));
            pomysly.add(new Rozkaz.ZakupUlepszenia(Ulepszenie.values()[bot.nextInt(Ulepszenie.values().length)]));
            pomysly.add(new Rozkaz.ZmianaMinistraFinansow(dzien.getKandydaciFinansow().get(bot.nextInt(2))));
            pomysly.add(new Rozkaz.ZmianaMinistraPracy(dzien.getKandydaciPracy().get(bot.nextInt(2))));
            if (!loch.getListaPotworow().isEmpty()) {
                Potwor potwor = loch.getListaPotworow().get(bot.nextInt(loch.getListaPotworow().size()));
                pomysly.add(new Rozkaz.Premia(potwor));
                if (bot.nextInt(10) == 0) {
                    pomysly.add(new Rozkaz.Zwolnienie(potwor));
                }
            }
            Rozkaz rozkaz = pomysly.get(bot.nextInt(pomysly.size()));
            if (bot.nextBoolean() && gra.powodOdmowy(rozkaz) == null) {
                gra.wykonajRozkaz(rozkaz);
            }
        }
    }

    private static void rozegrajDzien(Gra gra) {
        gra.rozegrajPoranek();
        Walka walka = gra.rozpocznijObrone();
        int kroki = 0;
        while (walka != null && !walka.czyZakonczona()) {
            gra.krokWalki();
            assertTrue(++kroki < 100_000, "walka musi się skończyć");
        }
        gra.zakonczDzien();
    }

    private static void sprawdzNiezmienniki(Gra gra) {
        Loch loch = gra.getLoch();
        assertTrue(loch.getSkarbiec().getZloto() >= 0);
        assertNotNull(loch.getMinisterFinansow());
        assertNotNull(loch.getMinisterPracy());
        for (Potwor potwor : loch.getListaPotworow()) {
            assertTrue(potwor.czyZyje(), "w lochu zostają tylko żywe potwory");
            assertEquals(potwor.getMaxPunktyZycia(), potwor.getPunktyZycia(), "wieczorem wszyscy są wyleczeni");
            assertTrue(potwor.getMorale() >= 0 && potwor.getMorale() <= potwor.getMaxMorale());
            assertTrue(!potwor.czyWKopalni());
        }
        assertEquals(loch.getListaPotworow().size(), loch.getListaPotworow().stream().distinct().count());
    }

    @Test
    void gryToczaSieBezBledowAzDoUpadkuLochu() {
        int zakonczone = 0;
        int sumaDni = 0;
        for (long ziarno = 1; ziarno <= 40; ziarno++) {
            Gra gra = nowaGra(ziarno);
            Random bot = new Random(ziarno * 31);
            while (!gra.czyKoniecGry() && gra.getNumerDnia() < 400) {
                wydajLosoweRozkazy(gra, bot);
                rozegrajDzien(gra);
                sprawdzNiezmienniki(gra);
                if (!gra.czyKoniecGry()) {
                    gra.rozpocznijDzien();
                }
            }
            if (gra.czyKoniecGry()) {
                zakonczone++;
            }
            sumaDni += gra.getNumerDnia();
        }
        assertTrue(zakonczone >= 30, "tryb bez końca: rosnąca trudność w końcu pokonuje loch (" + zakonczone + "/40)");
        assertTrue(sumaDni / 40 >= 8, "gra nie może kończyć się zbyt szybko (średnio dni: " + sumaDni / 40 + ")");
    }

    private static List<String> przebieg(long ziarno, int dni) {
        Gra gra = nowaGra(ziarno);
        Random bot = new Random(99);
        for (int i = 0; i < dni && !gra.czyKoniecGry(); i++) {
            wydajLosoweRozkazy(gra, bot);
            rozegrajDzien(gra);
            if (!gra.czyKoniecGry()) {
                gra.rozpocznijDzien();
            }
        }
        return gra.getDziennik().stream().map(Gra.WpisDziennika::tekst).toList();
    }

    @Test
    void toSamoZiarnoITeSameDecyzjeDajaTenSamPrzebieg() {
        assertEquals(przebieg(2024, 15), przebieg(2024, 15));
    }

    @Test
    void wczytanaGraToczySieIdentycznieJakOryginal(@TempDir Path katalog) throws Exception {
        Gra oryginal = nowaGra(77);
        for (int i = 0; i < 4; i++) {
            rozegrajDzien(oryginal);
            oryginal.rozpocznijDzien();
        }
        oryginal.wykonajRozkaz(new Rozkaz.ZmianaPostawy(Postawa.ODWOD));
        Path plik = katalog.resolve("zapis.dat");
        ZapisGry.zapisz(oryginal, plik);
        Gra wczytana = ZapisGry.wczytaj(plik).orElseThrow();

        assertEquals(oryginal.getNumerDnia(), wczytana.getNumerDnia());
        assertEquals(Postawa.ODWOD, wczytana.getLoch().getPostawa());
        assertEquals(1, wczytana.getDzien().getPozostaleRozkazy());
        for (int i = 0; i < 6; i++) {
            rozegrajDzien(oryginal);
            rozegrajDzien(wczytana);
            if (oryginal.czyKoniecGry()) {
                break;
            }
            oryginal.rozpocznijDzien();
            wczytana.rozpocznijDzien();
        }
        assertEquals(oryginal.getDziennik(), wczytana.getDziennik());
        assertEquals(oryginal.getLoch().getSkarbiec().getZloto(), wczytana.getLoch().getSkarbiec().getZloto());
    }

    @Test
    void brakZapisuAlboUszkodzonyPlikDajePustyWynik(@TempDir Path katalog) throws Exception {
        assertTrue(ZapisGry.wczytaj(katalog.resolve("nie-ma.dat")).isEmpty());
        Path zepsuty = katalog.resolve("zepsuty.dat");
        java.nio.file.Files.writeString(zepsuty, "to nie jest zapis gry");
        assertTrue(ZapisGry.wczytaj(zepsuty).isEmpty());
    }

    @Test
    void dziennieMoznaWydacTylkoDwaRozkazy() {
        Gra gra = nowaGra(5);
        assertEquals(2, gra.getDzien().getPozostaleRozkazy());
        gra.wykonajRozkaz(new Rozkaz.ZmianaPostawy(Postawa.ODWOD));
        gra.wykonajRozkaz(new Rozkaz.LimitRekrutacji(50));
        assertEquals("Brak rozkazów na dziś", gra.powodOdmowy(new Rozkaz.ZmianaPostawy(Postawa.PELNA_OBRONA)));
        assertThrows(IllegalStateException.class,
                () -> gra.wykonajRozkaz(new Rozkaz.ZmianaPostawy(Postawa.PELNA_OBRONA)));
        rozegrajDzien(gra);
        gra.rozpocznijDzien();
        assertEquals(2, gra.getDzien().getPozostaleRozkazy());
        assertNull(gra.powodOdmowy(new Rozkaz.ZmianaPostawy(Postawa.PELNA_OBRONA)));
    }

    @Test
    void samouczekOdblokowujeRozkazyStopniowoIZaczynaOdLatwegoAtaku() {
        Gra gra = Gra.nowaZSamouczkiem(3);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        Rozkaz ulepszenie = new Rozkaz.ZakupUlepszenia(Ulepszenie.KOPALNIA);
        Rozkaz postawa = new Rozkaz.ZmianaPostawy(Postawa.ODWOD);

        assertEquals(0, gra.getDzien().getPozostaleRozkazy(), "dzień 1: sama obserwacja");
        assertEquals("Dostępne od dnia 2", gra.powodOdmowy(ulepszenie));
        assertTrue(gra.getDzien().getBohaterowie().isEmpty());
        assertFalse(gra.getDzien().getKandydaci().isEmpty());

        rozegrajDzien(gra);
        gra.rozpocznijDzien();
        assertEquals(1, gra.getDzien().getPozostaleRozkazy());
        assertTrue(gra.czyNowy(ulepszenie.rodzaj()));
        assertEquals("Dostępne od dnia 3", gra.powodOdmowy(postawa));
        assertTrue(gra.getDzien().getBohaterowie().isEmpty());

        rozegrajDzien(gra);
        gra.rozpocznijDzien();
        assertNull(gra.powodOdmowy(postawa));
        assertEquals(1, gra.getDzien().getBohaterowie().size(), "dzień 3: jeden łatwy przeciwnik");
        assertTrue(gra.getDzien().getBohaterowie().get(0).getMaxPunktyZycia() <= 50);
        assertEquals(1, gra.getDzien().getRaport().minBohaterow());
        rozegrajDzien(gra);
        assertEquals(0, gra.getLoch().getPorazkiZRzedu(), "pierwszą walkę loch wygrywa");

        gra.rozpocznijDzien();
        rozegrajDzien(gra);
        gra.rozpocznijDzien();
        assertEquals(5, gra.getNumerDnia());
        assertEquals(2, gra.getDzien().getPozostaleRozkazy(), "od dnia 5 pełne dwa rozkazy");
        for (var rodzaj : wladcalochow.model.strategia.RodzajRozkazu.values()) {
            assertTrue(gra.czyOdblokowany(rodzaj));
        }
        assertTrue(gra.czyTrwaSamouczek());
        rozegrajDzien(gra);
        gra.rozpocznijDzien();
        assertFalse(gra.czyTrwaSamouczek());
    }

    @Test
    void bezSamouczkaWszystkoJestDostepneOdPierwszegoDnia() {
        Gra gra = nowaGra(3);
        assertEquals(2, gra.getDzien().getPozostaleRozkazy());
        assertNull(gra.powodOdmowy(new Rozkaz.ZmianaPostawy(Postawa.ODWOD)));
        assertFalse(gra.czyTrwaSamouczek());
    }

    @Test
    void zmianaMinistraDzialaOdNastepnegoDniaIKosztuje() {
        Gra gra = nowaGra(9);
        var obecny = gra.getLoch().getMinisterFinansow();
        var nowy = gra.getDzien().getKandydaciFinansow().get(0);
        int zlotoPrzed = gra.getLoch().getSkarbiec().getZloto();
        Rozkaz rozkaz = new Rozkaz.ZmianaMinistraFinansow(nowy);
        int koszt = gra.koszt(rozkaz);
        assertTrue(koszt > 0);
        gra.wykonajRozkaz(rozkaz);
        assertEquals(zlotoPrzed - koszt, gra.getLoch().getSkarbiec().getZloto());
        assertEquals(obecny, gra.getLoch().getMinisterFinansow(), "dziś jeszcze rządzi poprzedni minister");
        rozegrajDzien(gra);
        gra.rozpocznijDzien();
        assertEquals(nowy, gra.getLoch().getMinisterFinansow());
    }
}
