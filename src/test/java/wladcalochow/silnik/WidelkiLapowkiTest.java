package wladcalochow.silnik;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Loch;
import wladcalochow.model.Ogr;
import wladcalochow.model.RamboMinisterPracy;
import wladcalochow.model.RozrzutnyMinisterFinansow;
import wladcalochow.model.Skarbiec;
import wladcalochow.model.strategia.CechaMinistra;
import wladcalochow.model.strategia.RaportZwiadu;
import wladcalochow.model.strategia.WadaMinistra;

/** Prawdziwa cena łapówki zawsze mieści się w widełkach ze zwiadu: kto ma górną granicę, ten zapłaci. */
class WidelkiLapowkiTest {

    private static Loch loch(CechaMinistra cecha, WadaMinistra wada) {
        Loch loch = new Loch(new Skarbiec(800));
        loch.setMinisterFinansow(new RozrzutnyMinisterFinansow("F", 10, 0.2).zCecha(cecha, wada));
        loch.setMinisterPracy(new RamboMinisterPracy("P", 10, 0, 0));
        for (int i = 0; i < 8; i++) {
            loch.zatrudnij(new Ogr("Ogr" + i, 20, 90, 50, 10));
        }
        return loch;
    }

    @Test
    void cenaZawszeWWidelkach() {
        Random los = new Random(11);
        int sprawdzone = 0;
        for (Loch loch : new Loch[] {loch(null, null), loch(CechaMinistra.NEGOCJATOR, null),
                loch(null, WadaMinistra.NIESLOWNY)}) {
            for (int numer = 10; numer < 700; numer++) {
                if (numer % 7 == 0) {
                    loch.zanotujLapowke();  // droższe łapówki z rzędu też mają być w widełkach
                }
                if (numer % 23 == 0) {
                    loch.zanotujWynikObrony(true);
                }
                DzienGry dzien = GeneratorZdarzen.nowyDzien(numer, loch, los);
                RaportZwiadu raport = dzien.getRaport();
                if (raport.spokoj() || dzien.getBohaterowie().isEmpty()) {
                    continue;
                }
                int cena = dzien.kosztPrzekupstwa(loch);
                assertTrue(raport.lapowkaMin() <= cena && cena <= raport.lapowkaMax(),
                        "dzień " + numer + ": cena " + cena + " poza widełkami " + raport.widelki());
                sprawdzone++;
            }
        }
        assertTrue(sprawdzone > 1000, "sprawdzono " + sprawdzone);
    }
}
