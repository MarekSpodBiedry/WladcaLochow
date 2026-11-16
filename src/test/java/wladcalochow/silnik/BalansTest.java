package wladcalochow.silnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.DzienGry;

/** Zasady balansu: kolejność dnia i rozkład dochodu. */
class BalansTest {

    @Test
    void zlotoWplywaPrzedRozkazamiITylkoRaz() {
        Gra gra = Gra.nowa(11);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        int dochod = gra.getDzien().getDochodBazowy();
        assertEquals(Gra.ZLOTO_STARTOWE + dochod, gra.getLoch().getSkarbiec().getZloto(),
                "rano, przed rozkazami, dochód jest już w skarbcu");
        assertTrue(gra.getDziennik().stream().anyMatch(w -> w.tekst().startsWith("Przypływ złota")));

        int przedPorankiem = gra.getLoch().getSkarbiec().getZloto();
        gra.rozegrajPoranek();
        assertEquals(dochod, gra.getDzien().getBilans().getDochod(), "dochód liczy się raz");
        assertTrue(gra.getLoch().getSkarbiec().getZloto() <= przedPorankiem, "poranek nie dodaje dochodu drugi raz");
    }

    @Test
    void lupyTrafiajaDoSkarbcaAPotworyNieNoszaZlota() {
        int dniZLupem = 0;
        for (long ziarno = 1; ziarno <= 20; ziarno++) {
            Gra gra = Gra.nowa(ziarno);
            gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
            for (int dzien = 0; dzien < 25 && !gra.czyKoniecGry(); dzien++) {
                gra.rozegrajPoranek();
                if (gra.rozpocznijObrone() != null) {
                    while (!gra.getDzien().getWalka().czyZakonczona()) {
                        gra.krokWalki();
                    }
                }
                gra.zakonczDzien();
                assertTrue(gra.getLoch().getListaPotworow().stream().allMatch(p -> p.getZloto() == 0),
                        "po dniu żaden potwór nie trzyma złota");
                if (gra.getDzien().getBilans().getLupy() > 0) {
                    dniZLupem++;
                }
                if (!gra.czyKoniecGry()) {
                    gra.rozpocznijDzien();
                }
            }
        }
        assertTrue(dniZLupem > 0, "łupy z walk trafiły do skarbca");
    }

    @Test
    void bohaterowNieJestWiecejNizPotworowPlusJeden() {
        Random los = new Random(8);
        for (int potwory = 0; potwory <= 12; potwory++) {
            for (int i = 0; i < 200; i++) {
                int ilu = GeneratorZdarzen.losujBohaterow(40, 5000, potwory, los).size();
                assertTrue(ilu <= potwory + 1, potwory + " potworów, a bohaterów " + ilu);
            }
        }
        // Silny loch późno w grze nadal dostaje duże ataki.
        int najwiecej = 0;
        for (int i = 0; i < 200; i++) {
            najwiecej = Math.max(najwiecej, GeneratorZdarzen.losujBohaterow(40, 5000, 12, los).size());
        }
        assertEquals(GeneratorZdarzen.MAX_BOHATEROW, najwiecej);
    }

    @Test
    void zerowyDochodJestRzadki() {
        Gra gra = Gra.nowa(3);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        Random los = new Random(5);
        int zerowe = 0;
        int dni = 2000;
        for (int i = 0; i < dni; i++) {
            DzienGry dzien = GeneratorZdarzen.nowyDzien(10, gra.getLoch(), los);
            if (dzien.getDochodBazowy() == 0) {
                zerowe++;
            } else {
                assertTrue(dzien.getDochodBazowy() >= 40, "niezerowy dochód to co najmniej 40");
            }
        }
        assertTrue(zerowe > 0 && zerowe < dni / 10, "zerowy dochód zdarza się, ale rzadko: " + zerowe);
    }
}
