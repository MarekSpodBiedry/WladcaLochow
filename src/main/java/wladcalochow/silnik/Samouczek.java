package wladcalochow.silnik;

import wladcalochow.model.DzienGry;

/** Zasady łagodnego startu: pierwsze dni mają mniej decyzji i jeden łatwy atak. */
public final class Samouczek {

    /** Ostatni dzień samouczka; od tego dnia gra toczy się na pełnych zasadach. */
    public static final int OSTATNI_DZIEN = 5;
    /** Dzień pierwszego, celowo łatwego ataku. */
    public static final int DZIEN_PIERWSZEGO_ATAKU = 3;

    private Samouczek() {
    }

    /** Ile rozkazów wolno wydać danego dnia samouczka: najpierw zero, potem jeden, na końcu dwa. */
    public static int rozkazyNaDzien(int dzien) {
        if (dzien <= 1) {
            return 0;
        }
        return dzien < OSTATNI_DZIEN ? 1 : DzienGry.ROZKAZY_NA_DZIEN;
    }
}
