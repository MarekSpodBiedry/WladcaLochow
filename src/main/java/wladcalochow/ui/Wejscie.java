package wladcalochow.ui;

/** Jedno zdarzenie wejścia: znak, klawisz specjalny albo kliknięcie myszką. */
sealed interface Wejscie {

    enum Rodzaj { GORA, DOL, LEWO, PRAWO, ENTER, ESC, TAB, BACKSPACE, STRONA_W_GORE, STRONA_W_DOL }

    record Znak(char znak) implements Wejscie {
    }

    record Klawisz(Rodzaj rodzaj) implements Wejscie {
    }

    /** Kliknięcie lewym przyciskiem; współrzędne komórki liczone od zera. */
    record Klik(int x, int y) implements Wejscie {
    }

    /** Nic nie nadeszło w zadanym czasie. */
    record Brak() implements Wejscie {
    }

    /** Koniec wejścia albo Ctrl+C. */
    record Koniec() implements Wejscie {
    }
}
