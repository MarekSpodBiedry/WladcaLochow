package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.Ogr;
import wladcalochow.model.Ork;
import wladcalochow.model.Potwor;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.silnik.Gra;

/** Ciężko ranni: kilka dni poza walką i kopalnią, z żołdem; zwolnienie rannego boli całą załogę. */
class RanniTest {

    private static Gra gra() {
        Gra gra = Gra.nowa(4);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        return gra;
    }

    @Test
    void rannyNieWalczyINieKopieAleDostajeZoldIWraca() {
        Gra gra = gra();
        Ork ranny = new Ork("Ranny", 14, 60, 70, 20);
        ranny.zranCiezko(3);
        gra.getLoch().zatrudnij(ranny);
        for (int i = 0; i < 6; i++) {
            gra.getLoch().zatrudnij(new Ogr("Zdrowy" + i, 20, 100, 90, 1));
        }
        gra.getLoch().getSkarbiec().wplac(5000);

        gra.rozegrajPoranek();
        assertTrue(ranny.czyRanny());
        assertFalse(ranny.czyWKopalni(), "ranny nie kopie");
        assertFalse(gra.getLoch().wojownicy().contains(ranny), "ranny nie walczy");
        assertTrue(gra.getLoch().naZoldzie().contains(ranny), "żołd należy się także rannemu");

        int dni = 0;
        while (ranny.czyRanny() && dni < 10) {
            var walka = gra.rozpocznijObrone();
            while (walka != null && !walka.czyZakonczona()) {
                gra.krokWalki();
            }
            gra.zakonczDzien();
            gra.rozpocznijDzien();
            gra.rozegrajPoranek();
            dni++;
        }
        assertFalse(ranny.czyRanny(), "ranny wraca do sił");
        assertEquals(2, dni, "ranny na 3 poranki opuszcza 2 dni");
        assertTrue(gra.getDziennik().stream().anyMatch(w -> w.tekst().startsWith("Po ranach wracają do sił")));
    }

    @Test
    void zwolnienieRannegoObnizaMoraleReszty() {
        Gra gra = gra();
        Ork ranny = new Ork("Ranny", 14, 60, 70, 20);
        ranny.zranCiezko(3);
        gra.getLoch().zatrudnij(ranny);
        List<Integer> przed = new ArrayList<>();
        for (Potwor p : gra.getLoch().getListaPotworow()) {
            if (p != ranny) {
                przed.add(p.getMorale());
            }
        }
        gra.wykonajRozkaz(new Rozkaz.Zwolnienie(ranny));
        List<Integer> po = gra.getLoch().getListaPotworow().stream().map(Potwor::getMorale).toList();
        for (int i = 0; i < po.size(); i++) {
            assertTrue(po.get(i) < przed.get(i), "morale spada po wyrzuceniu rannego");
        }
    }

    @Test
    void szpitalStawiaRannegoNaNogiOdRazuZaZoldZaPozostaleDni() {
        Gra gra = gra();
        Ork ranny = new Ork("Ranny", 14, 60, 70, 20);
        ranny.zranCiezko(3);
        gra.getLoch().zatrudnij(ranny);
        gra.getLoch().getSkarbiec().wplac(500);
        Rozkaz szpital = new Rozkaz.Szpital(ranny);
        assertEquals(120, gra.koszt(szpital), "podwójny żołd 20 za każdy z 3 pozostałych dni");
        int zloto = gra.getLoch().getSkarbiec().getZloto();

        gra.wykonajRozkaz(szpital);
        assertFalse(ranny.czyRanny());
        assertEquals(zloto - 120, gra.getLoch().getSkarbiec().getZloto());
        gra.rozegrajPoranek();
        assertTrue(gra.getLoch().wojownicy().contains(ranny) || ranny.czyWKopalni(), "już dziś walczy albo kopie");
    }

    @Test
    void szpitalTylkoDlaRannegoICoNajmniej15Zlota() {
        Gra gra = gra();
        Ork zdrowy = new Ork("Zdrowy", 14, 60, 70, 3);
        gra.getLoch().zatrudnij(zdrowy);
        assertEquals("Ten potwór nie jest ranny", gra.powodOdmowy(new Rozkaz.Szpital(zdrowy)));
        zdrowy.zranCiezko(2);
        assertEquals(Rozkaz.Szpital.MIN_KOSZT, gra.koszt(new Rozkaz.Szpital(zdrowy)), "tani potwór: cena minimalna");
    }

    @Test
    void zwolnienieZdrowegoNieRuszaMorale() {
        Gra gra = gra();
        Ork zdrowy = new Ork("Zdrowy", 14, 60, 70, 20);
        gra.getLoch().zatrudnij(zdrowy);
        List<Integer> przed = gra.getLoch().getListaPotworow().stream().filter(p -> p != zdrowy)
                .map(Potwor::getMorale).toList();
        gra.wykonajRozkaz(new Rozkaz.Zwolnienie(zdrowy));
        assertEquals(przed, gra.getLoch().getListaPotworow().stream().map(Potwor::getMorale).toList());
    }

    @Test
    void ranniPrzyciagajaSilniejszeAtaki() {
        Random los = new Random(3);
        List<Potwor> zdrowi = new ArrayList<>();
        List<Potwor> zRannymi = new ArrayList<>();
        // Ta sama szóstka zdrowych: raz sama (ranni zwolnieni), raz z trzema rannymi w lochu.
        for (int i = 0; i < 6; i++) {
            Ogr ogr = new Ogr("A" + i, 20, 100, 90, 30);
            zdrowi.add(ogr);
            zRannymi.add(ogr);
        }
        for (int i = 0; i < 3; i++) {
            Ogr ranny = new Ogr("B" + i, 20, 100, 90, 30);
            ranny.zranCiezko(3);
            zRannymi.add(ranny);
        }
        long sumaZdrowi = 0;
        long sumaRanni = 0;
        for (int i = 0; i < 3000; i++) {
            sumaZdrowi += wladcalochow.model.DzienGry.wartoscOddzialu(
                    wladcalochow.silnik.GeneratorZdarzenTestowy.bohaterowie(60, zdrowi, los));
            sumaRanni += wladcalochow.model.DzienGry.wartoscOddzialu(
                    wladcalochow.silnik.GeneratorZdarzenTestowy.bohaterowie(60, zRannymi, los));
        }
        assertTrue(sumaRanni > sumaZdrowi * 1.1, "ranni przyciągają bohaterów: " + sumaRanni + " vs " + sumaZdrowi);
    }
}
