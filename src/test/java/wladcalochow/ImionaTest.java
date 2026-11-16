package wladcalochow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wladcalochow.model.Istota;
import wladcalochow.model.Loch;
import wladcalochow.silnik.FabrykaIstot;
import wladcalochow.silnik.GeneratorZdarzen;

class ImionaTest {

    /** Listy na ekranie rezerwują stałą szerokość na imię, więc żadne nie może być dłuższe. */
    @Test
    void imionaMieszczaSieWKolumnieINigdyNieDostajaNumeru() {
        Random los = new Random(1);
        for (int proba = 0; proba < 300; proba++) {
            Set<String> zajete = new HashSet<>();
            // najgorszy przypadek: pełny loch i komplet kandydatów jednego rodzaju
            for (int i = 0; i < Loch.POJEMNOSC + 6; i++) {
                sprawdz(FabrykaIstot.goblin(los, zajete));
            }
            zajete.clear();
            for (int i = 0; i < Loch.POJEMNOSC + 6; i++) {
                sprawdz(FabrykaIstot.ork(los, zajete));
            }
            zajete.clear();
            for (int i = 0; i < Loch.POJEMNOSC + 6; i++) {
                sprawdz(FabrykaIstot.ogr(los, zajete));
            }
            zajete.clear();
            for (int i = 0; i < GeneratorZdarzen.MAX_BOHATEROW; i++) {
                sprawdz(FabrykaIstot.rycerz(los, zajete));
            }
            zajete.clear();
            for (int i = 0; i < GeneratorZdarzen.MAX_BOHATEROW; i++) {
                sprawdz(FabrykaIstot.lucznik(los, zajete));
            }
            zajete.clear();
            for (int i = 0; i < GeneratorZdarzen.MAX_BOHATEROW; i++) {
                sprawdz(FabrykaIstot.zlodziej(los, zajete));
            }
            assertEquals(GeneratorZdarzen.MAX_BOHATEROW, zajete.size(), "imiona w jednym ataku są różne");
        }
    }

    private static void sprawdz(Istota istota) {
        String imie = istota.getImie();
        assertTrue(imie.length() <= FabrykaIstot.MAX_DLUGOSC_IMIENIA, "za długie imię: " + imie);
        assertTrue(imie.chars().noneMatch(Character::isDigit), "imię z numerem: " + imie);
    }
}
