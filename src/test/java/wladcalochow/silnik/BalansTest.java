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

    /** Średnia wartość ataku przy danej załodze (bez dni spokoju). */
    private static double sredniAtak(java.util.List<wladcalochow.model.Potwor> potwory, int dzien) {
        Random los = new Random(8);
        long suma = 0;
        for (int i = 0; i < 2000; i++) {
            suma += DzienGry.wartoscOddzialu(GeneratorZdarzen.losujBohaterow(dzien, 0, potwory, los));
        }
        return suma / 2000.0;
    }

    @Test
    void silaAtakuZalezyOdSilyLochuANieTylkoOdDnia() {
        Random los = new Random(1);
        java.util.Set<String> imiona = new java.util.HashSet<>();
        java.util.List<wladcalochow.model.Potwor> gobliny = new java.util.ArrayList<>();
        java.util.List<wladcalochow.model.Potwor> ogry = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            gobliny.add(FabrykaIstot.goblin(los, imiona));
            ogry.add(FabrykaIstot.ogr(los, imiona));
        }
        double naGobliny = sredniAtak(gobliny, 60);
        double naOgry = sredniAtak(ogry, 60);
        assertTrue(naOgry > 2 * naGobliny, "silny loch dostaje mocniejsze ataki: gobliny " + naGobliny + ", ogry " + naOgry);
        // Atak na ten sam loch nie rośnie bez końca z numerem dnia.
        assertEquals(sredniAtak(ogry, 60), sredniAtak(ogry, 500), 1.0);
    }

    @Test
    void czescPokonanychPotworowPrzezywaAleWiekszoscGinie() {
        int ranni = 0;
        int polegli = 0;
        for (long ziarno = 1; ziarno <= 60; ziarno++) {
            Gra gra = Gra.nowa(ziarno);
            gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
            for (int dzien = 0; dzien < 30 && !gra.czyKoniecGry(); dzien++) {
                gra.rozegrajPoranek();
                if (gra.rozpocznijObrone() != null) {
                    while (!gra.getDzien().getWalka().czyZakonczona()) {
                        gra.krokWalki();
                    }
                }
                gra.zakonczDzien();
                polegli += gra.getDzien().getBilans().getPolegli().size();
                if (!gra.czyKoniecGry()) {
                    gra.rozpocznijDzien();
                }
            }
            ranni += (int) gra.getDziennik().stream().filter(w -> w.tekst().startsWith("Przeżyli ciężko ranni")).count();
        }
        assertTrue(ranni > 0, "zdarzają się ocaleni");
        assertTrue(polegli > ranni, "większość pokonanych ginie: polegli " + polegli + ", dni z rannymi " + ranni);
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
