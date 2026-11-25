package wladcalochow.silnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.BylejakiMinisterPracy;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Loch;
import wladcalochow.model.Ogr;
import wladcalochow.model.RozrzutnyMinisterFinansow;
import wladcalochow.model.Skarbiec;
import wladcalochow.model.strategia.RaportZwiadu;

/** Darmowi stażyści czasem coś psują, żeby nie opłacało się celowo nie płacić ministrom. */
class StazysciTest {

    private static Loch loch(boolean stazystaF, boolean stazystkaP) {
        Loch loch = new Loch(new Skarbiec(500));
        loch.setMinisterFinansow(new RozrzutnyMinisterFinansow(stazystaF ? "Stażysta Wicek" : "F", stazystaF ? 0 : 10, 0));
        loch.setMinisterPracy(new BylejakiMinisterPracy(stazystkaP ? "Stażystka Bronka" : "P", stazystkaP ? 0 : 10, 0, 0));
        for (int i = 0; i < 6; i++) {
            loch.zatrudnij(new Ogr("Ogr" + i, 20, 90, 50, 10));
        }
        return loch;
    }

    private static int dniWygadania(Loch loch) {
        Random los = new Random(3);
        int dni = 0;
        for (int numer = 10; numer < 510; numer++) {
            if (GeneratorZdarzen.nowyDzien(numer, loch, los).czyWygadanySkarbiec()) {
                dni++;
            }
        }
        return dni;
    }

    @Test
    void stazystaFinansowCzasemWygadujeSieOSkarbcu() {
        int dni = dniWygadania(loch(true, false));
        assertTrue(dni > 60 && dni < 140, "ok. 20% z 500 dni, jest " + dni);
        assertEquals(0, dniWygadania(loch(false, false)), "płatny minister trzyma język za zębami");
    }

    private static int zwolnieniPrzezPomylke(boolean stazystka) {
        Random los = new Random(5);
        int zwolnieni = 0;
        for (int i = 0; i < 300; i++) {
            Loch loch = loch(false, stazystka);
            DzienGry dzien = new DzienGry(10, 50, List.of(), List.of(), List.of(), List.of(),
                    new RaportZwiadu(0, 0, null, 0), 500);
            dzien.rozegrajPoranek(loch, los);
            zwolnieni += 6 - loch.getListaPotworow().size();
        }
        return zwolnieni;
    }

    @Test
    void stazystkaKadrCzasemZwalniaPrzezPomylke() {
        int zwolnieni = zwolnieniPrzezPomylke(true);
        assertTrue(zwolnieni > 15 && zwolnieni < 50, "ok. 10% z 300 poranków, jest " + zwolnieni);
        assertEquals(0, zwolnieniPrzezPomylke(false), "płatny minister nikogo nie zwalnia");
    }
}
