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
