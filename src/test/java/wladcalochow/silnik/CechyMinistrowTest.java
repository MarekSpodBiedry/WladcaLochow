package wladcalochow.silnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.BylejakiMinisterPracy;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Loch;
import wladcalochow.model.Ogr;
import wladcalochow.model.Ork;
import wladcalochow.model.Potwor;
import wladcalochow.model.RozrzutnyMinisterFinansow;
import wladcalochow.model.Rycerz;
import wladcalochow.model.Skarbiec;
import wladcalochow.model.strategia.CechaMinistra;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.RaportZwiadu;
import wladcalochow.model.strategia.WadaMinistra;

/** Każda cecha i wada ministra działa tak, jak opisuje to gra. */
class CechyMinistrowTest {

    private static Loch loch(int zloto, CechaMinistra cechaF, WadaMinistra wadaF, CechaMinistra cechaP, WadaMinistra wadaP) {
        Loch loch = new Loch(new Skarbiec(zloto));
        loch.setMinisterFinansow(new RozrzutnyMinisterFinansow("F", 10, 0).zCecha(cechaF, wadaF));
        loch.setMinisterPracy(new BylejakiMinisterPracy("P", 10, 0, 0).zCecha(cechaP, wadaP));
        return loch;
    }

    private static DzienGry dzien(int dochod, List<wladcalochow.model.Bohater> bohaterowie) {
        return new DzienGry(5, dochod, List.of(), bohaterowie, List.of(), List.of(),
                new RaportZwiadu(0, 0, null, 0), 0);
    }

    @Test
    void kazdyMinisterMaCechePasujacaDoUrzeduIWade() {
        Random los = new Random(1);
        for (int i = 0; i < 300; i++) {
            var mf = FabrykaIstot.ministerFinansow(los, los.nextBoolean(), new HashSet<>());
            var mp = FabrykaIstot.ministerPracy(los, los.nextBoolean(), new HashSet<>());
            assertTrue(mf.getCecha().finansowa() && mf.getWada() != null);
            assertTrue(!mp.getCecha().finansowa() && mp.getWada() != null);
        }
    }

    @Test
    void negocjatorObnizaLapowke() {
        List<wladcalochow.model.Bohater> bohaterowie = List.of(new Rycerz("Sir", 16, 80, 90, 0));
        int zwykla = dzien(0, bohaterowie).kosztPrzekupstwa(loch(0, null, null, null, null));
        int negocjator = dzien(0, bohaterowie).kosztPrzekupstwa(loch(0, CechaMinistra.NEGOCJATOR, null, null, null));
        assertEquals(Math.round(zwykla * 0.75), negocjator, 1);
    }

    @Test
    void lichwiarzDajeOdsetkiDoLimitu() {
        Loch maly = loch(1000, CechaMinistra.LICHWIARZ, null, null, null);
        dzien(0, List.of()).przyjmijPrzychod(maly);
        assertEquals(1020, maly.getSkarbiec().getZloto());
        Loch duzy = loch(100_000, CechaMinistra.LICHWIARZ, null, null, null);
        dzien(0, List.of()).przyjmijPrzychod(duzy);
        assertEquals(100_000 + DzienGry.MAX_ODSETEK, duzy.getSkarbiec().getZloto());
    }

    @Test
    void pensjaRosnieZPrzychodemAChciwyBierzeWiecejAStazystaNic() {
        var zwykly = new RozrzutnyMinisterFinansow("Z", 10, 0);
        var chciwy = new RozrzutnyMinisterFinansow("C", 10, 0).zCecha(null, WadaMinistra.CHCIWY);
        assertEquals(10 + 30, zwykly.pensjaDnia(1000));
        assertEquals(10 + 80, chciwy.pensjaDnia(1000));
        assertEquals(0, new RozrzutnyMinisterFinansow("Stażysta", 0, 0).pensjaDnia(1000));
    }

    @Test
    void medykLeczyDwaRazySzybciej() {
        Loch loch = loch(1000, null, null, CechaMinistra.MEDYK, null);
        Ork ranny = new Ork("R", 14, 60, 70, 20);
        ranny.zranCiezko(2);
        loch.zatrudnij(ranny);
        dzien(0, List.of()).rozegrajPoranek(loch, new Random(1));
        assertTrue(!ranny.czyRanny(), "dwa poranki rekonwalescencji w jeden");
    }

    @Test
    void nadzorcaZwiekszaWydobycie() {
        assertEquals(1.25, loch(0, null, null, CechaMinistra.NADZORCA, null).mnoznikWydobycia(), 1e-9);
        assertEquals(1.0, loch(0, null, null, CechaMinistra.MEDYK, null).mnoznikWydobycia(), 1e-9);
    }

    @Test
    void trenerPodwajaDoswiadczenie() {
        Ogr zwykly = new Ogr("A", 20, 100, 90, 30);
        Ogr trenowany = new Ogr("B", 20, 100, 90, 30);
        int awanseZwykle = 0;
        int awanseTrenowane = 0;
        for (int i = 0; i < 6; i++) {
            awanseZwykle += zwykly.podsumujWalke(false) ? 1 : 0;
            awanseTrenowane += trenowany.podsumujWalke(true) ? 1 : 0;
        }
        assertTrue(trenowany.getPoziom() > zwykly.getPoziom(), "trenowany awansuje szybciej");
        assertTrue(awanseTrenowane > awanseZwykle);
    }

    @Test
    void werbownikPrzyciagaOgry() {
        Random los = new Random(5);
        int zwykle = 0;
        int werbowane = 0;
        for (int i = 0; i < 4000; i++) {
            zwykle += FabrykaIstot.losowyPotwor(los, new HashSet<>(), false) instanceof Ogr ? 1 : 0;
            werbowane += FabrykaIstot.losowyPotwor(los, new HashSet<>(), true) instanceof Ogr ? 1 : 0;
        }
        assertTrue(werbowane > zwykle * 1.7, "ogry: zwykle " + zwykle + ", u werbownika " + werbowane);
    }

    @Test
    void plotkarzWzmacniaAtakiANieslownyPsujeZwiad() {
        Random los = new Random(2);
        List<Potwor> potwory = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            potwory.add(new Ogr("O" + i, 20, 100, 90, 30));
        }
        long zwykle = 0;
        long plotki = 0;
        for (int i = 0; i < 3000; i++) {
            zwykle += DzienGry.wartoscOddzialu(GeneratorZdarzen.losujBohaterow(60, 0, potwory, false, 1.0, los));
            plotki += DzienGry.wartoscOddzialu(GeneratorZdarzen.losujBohaterow(60, 0, potwory, false,
                    1 + GeneratorZdarzen.PLOTKI, los));
        }
        assertTrue(plotki > zwykle * 1.05, "plotkarz ściąga silniejsze ataki");
        RaportZwiadu raport = GeneratorZdarzen.zwiad(List.of(new Rycerz("Sir", 16, 80, 90, 0)), los, 1.0, true);
        assertNull(raport.ujawnionyRodzaj(), "niesłowny nie podaje rodzaju bohatera");
    }

    @Test
    void stylNieZmieniaSieOdCechy() {
        assertEquals(Postawa.PELNA_OBRONA, loch(0, CechaMinistra.NEGOCJATOR, WadaMinistra.PLOTKARZ, null, null).getPostawa());
    }
}
