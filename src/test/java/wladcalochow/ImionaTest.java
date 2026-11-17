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

    /** Jedno imię to zawsze ten sam rodzaj: ministra (styl) i istoty. */
    @Test
    void jednoImieToZawszeTenSamRodzaj() {
        Random los = new Random(2);
        java.util.Map<String, Class<?>> rodzaje = new java.util.HashMap<>();
        for (int proba = 0; proba < 2000; proba++) {
            Set<String> zajete = new HashSet<>();
            boolean styl = los.nextBoolean();
            Object[] obiekty = {
                FabrykaIstot.ministerFinansow(los, styl, zajete), FabrykaIstot.ministerPracy(los, styl, zajete),
                FabrykaIstot.losowyPotwor(los, zajete), FabrykaIstot.losowyBohater(los, zajete)};
            for (Object obiekt : obiekty) {
                String imie = obiekt instanceof Istota istota ? istota.getImie()
                        : obiekt instanceof wladcalochow.model.MinisterFinansow mf ? mf.getImie()
                        : ((wladcalochow.model.MinisterPracy) obiekt).getImie();
                Class<?> poprzedni = rodzaje.putIfAbsent(imie, obiekt.getClass());
                assertTrue(poprzedni == null || poprzedni == obiekt.getClass(),
                        () -> imie + " to raz " + poprzedni.getSimpleName() + ", a raz " + obiekt.getClass().getSimpleName());
            }
        }
    }

    private static void sprawdz(Istota istota) {
        String imie = istota.getImie();
        assertTrue(imie.length() <= FabrykaIstot.MAX_DLUGOSC_IMIENIA, "za długie imię: " + imie);
        assertTrue(imie.chars().noneMatch(Character::isDigit), "imię z numerem: " + imie);
    }
}
