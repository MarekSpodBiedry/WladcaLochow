package wladcalochow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import wladcalochow.model.Goblin;
import wladcalochow.model.Ogr;
import wladcalochow.model.Ork;
import wladcalochow.model.Potwor;

/** Potwory w panelu i w oknie idą od najsilniejszego, a lista lochu (numery P1.. dla Claude'a) się nie zmienia. */
class KolejnoscPotworowTest {

    @Test
    void potworyIdaOdNajsilniejszego() {
        Potwor slaby = new Goblin("Mały", 5, 20, 50, 3);
        Potwor sredni = new Ork("Średni", 12, 50, 50, 6);
        Potwor silny = new Ogr("Duży", 20, 90, 50, 10);
        List<Potwor> loch = List.of(slaby, silny, sredni);

        assertEquals(List.of(silny, sredni, slaby), Aplikacja.wedlugMocy(loch));
        assertEquals(List.of(slaby, silny, sredni), loch, "kolejność w lochu zostaje");
    }

    @Test
    void mocUwzgledniaRodzaj() {
        // Ta sama siła i życie: goblin (ogłusza) jest wart więcej niż ork.
        Potwor goblin = new Goblin("G", 10, 40, 50, 5);
        Potwor ork = new Ork("O", 10, 40, 50, 5);
        assertEquals(List.of(goblin, ork), Aplikacja.wedlugMocy(List.of(ork, goblin)));
    }

    @Test
    void rownaMocZostawiaKolejnoscLochu() {
        Potwor a = new Ork("A", 10, 40, 50, 5);
        Potwor b = new Ork("B", 10, 40, 50, 5);
        assertEquals(List.of(a, b), Aplikacja.wedlugMocy(List.of(a, b)));
    }
}
