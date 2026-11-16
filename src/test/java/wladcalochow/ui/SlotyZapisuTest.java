package wladcalochow.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Loch;
import wladcalochow.model.Potwor;
import wladcalochow.silnik.Gra;
import wladcalochow.silnik.ZapisGry;

/** Sloty zapisu w interfejsie: gra sama nigdy nie usuwa ani nie nadpisuje cudzego zapisu. */
class SlotyZapisuTest {

    private static final int SZER = Aplikacja.MIN_SZER + 16;
    private static final int WYS = Aplikacja.MIN_WYS + 30;

    private static Gra rozpoczeta(long ziarno) {
        Gra gra = Gra.nowa(ziarno);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        return gra;
    }

    private static void enter(Aplikacja aplikacja) {
        aplikacja.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER));
        aplikacja.rysuj();
    }

    private static void znak(Aplikacja aplikacja, char znak) {
        aplikacja.obsluz(new Wejscie.Znak(znak));
        aplikacja.rysuj();
    }

    /** Rano zabiera lochowi potwory i złoto, aż loch upadnie. */
    private static void doprowadzDoUpadku(Aplikacja aplikacja, Gra gra) {
        for (int krok = 0; krok < 3000 && !gra.czyKoniecGry(); krok++) {
            if (!aplikacja.czyDialog() && gra.getDzien().getFaza() == DzienGry.Faza.PORANEK) {
                Loch loch = gra.getLoch();
                for (Potwor potwor : new ArrayList<>(loch.getListaPotworow())) {
                    loch.usun(potwor);
                }
                loch.getSkarbiec().wyplac(loch.getSkarbiec().getZloto());
            }
            enter(aplikacja);
        }
        assertTrue(gra.czyKoniecGry(), "loch miał upaść");
    }

    @Test
    void przegranaZostajeWSlocieIDaSieJaObejrzec(@TempDir Path folder) {
        Aplikacja aplikacja = new Aplikacja(new KonsolaBezEkranu(SZER, WYS), folder, 3L);
        Gra gra = rozpoczeta(3);
        aplikacja.pokazGre(gra, 2);
        aplikacja.rysuj();
        doprowadzDoUpadku(aplikacja, gra);

        Path plik = ZapisGry.plikSlotu(folder, 2);
        assertTrue(Files.exists(plik), "po przegranej zapis musi zostać");
        assertTrue(ZapisGry.wczytaj(plik).orElseThrow().czyKoniecGry());

        // Wyjście do menu i ponowne uruchomienie gry niczego nie kasuje.
        aplikacja.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ESC));
        znak(aplikacja, 'q');
        aplikacja = new Aplikacja(new KonsolaBezEkranu(SZER, WYS), folder, 4L);
        aplikacja.rysuj();
        assertTrue(Files.exists(plik));
        assertFalse(aplikacja.dostepneSkroty().contains('k'), "przegranej gry nie da się kontynuować");
        assertTrue(aplikacja.dostepneSkroty().contains('w'));

        // Wczytaj: slot 2 to druga opcja na liście; otwiera przegraną grę do obejrzenia.
        znak(aplikacja, 'w');
        assertTrue(aplikacja.czyDialog());
        znak(aplikacja, '2');
        assertNotNull(aplikacja.gra());
        assertTrue(aplikacja.gra().czyKoniecGry());
        assertTrue(aplikacja.czyDialog(), "po wczytaniu widać okno Loch upadł");
        enter(aplikacja); // Obejrzyj loch
        assertFalse(aplikacja.czyDialog());
        assertTrue(aplikacja.klatka().tekst().contains("Podsumowanie"));
        assertTrue(Files.exists(plik));
    }

    @Test
    void kontynuujWczytujeOstatniaGreWToku(@TempDir Path folder) throws Exception {
        ZapisGry.zapisz(rozpoczeta(1), ZapisGry.plikSlotu(folder, 5));
        Aplikacja aplikacja = new Aplikacja(new KonsolaBezEkranu(SZER, WYS), folder, 1L);
        aplikacja.rysuj();
        znak(aplikacja, 'k');
        assertNotNull(aplikacja.gra());
        assertFalse(aplikacja.gra().czyKoniecGry());
    }

    @Test
    void nowaGraPrzyPelnychSlotachNicNieZmieniaBezWyboru(@TempDir Path folder) throws Exception {
        List<byte[]> przed = new ArrayList<>();
        for (int numer = 1; numer <= ZapisGry.SLOTY; numer++) {
            Path plik = ZapisGry.plikSlotu(folder, numer);
            ZapisGry.zapisz(rozpoczeta(numer), plik);
            przed.add(Files.readAllBytes(plik));
        }
        Aplikacja aplikacja = new Aplikacja(new KonsolaBezEkranu(SZER, WYS), folder, 9L);
        aplikacja.rysuj();
        znak(aplikacja, 'n');
        assertTrue(aplikacja.czyDialog());
        assertTrue(aplikacja.klatka().tekst().contains("wszystkie sloty zajęte"));
        aplikacja.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ESC));
        aplikacja.rysuj();

        // Wybór slotu 4 i rezygnacja przed wyborem ministrów: żaden plik się nie zmienia.
        znak(aplikacja, 'n');
        znak(aplikacja, '4');
        assertTrue(aplikacja.klatka().tekst().contains("slot 4"));
        aplikacja.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ESC));
        aplikacja.rysuj();
        for (int numer = 1; numer <= ZapisGry.SLOTY; numer++) {
            assertArrayEquals(przed.get(numer - 1), Files.readAllBytes(ZapisGry.plikSlotu(folder, numer)),
                    "slot " + numer + " nie mógł się zmienić");
        }
    }

    @Test
    void nowaGraTrafiaDoPierwszegoWolnegoSlotu(@TempDir Path folder) throws Exception {
        ZapisGry.zapisz(rozpoczeta(1), ZapisGry.plikSlotu(folder, 1));
        Aplikacja aplikacja = new Aplikacja(new KonsolaBezEkranu(SZER, WYS), folder, 9L);
        aplikacja.rysuj();
        znak(aplikacja, 'n');
        assertTrue(aplikacja.klatka().tekst().contains("slot 2"));
        znak(aplikacja, '2'); // bez samouczka
        znak(aplikacja, '1'); // Minister Finansów
        znak(aplikacja, '1'); // Minister Pracy
        assertTrue(Files.exists(ZapisGry.plikSlotu(folder, 2)));
        assertEquals(2, ZapisGry.sloty(folder).stream().filter(ZapisGry.Slot::zajety).count());
    }
}
