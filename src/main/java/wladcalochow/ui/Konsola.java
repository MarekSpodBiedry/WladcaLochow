package wladcalochow.ui;

import java.io.IOException;

/** To, czego interfejs gry potrzebuje od terminala. Pozwala testować układ ekranu bez terminala. */
interface Konsola extends AutoCloseable {

    int szer();

    int wys();

    boolean pelnyKolor();

    void wypisz(String tekst);

    /** Czeka najwyżej podany czas na jedno zdarzenie wejścia. */
    Wejscie czytaj(long milisekundy) throws IOException;

    @Override
    void close() throws IOException;
}
