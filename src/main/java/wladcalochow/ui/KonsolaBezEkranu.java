package wladcalochow.ui;

/** Terminal o stałym rozmiarze, który niczego nie wyświetla. Służy do autotestu gry. */
final class KonsolaBezEkranu implements Konsola {

    private final int szer;
    private final int wys;

    KonsolaBezEkranu(int szer, int wys) {
        this.szer = szer;
        this.wys = wys;
    }

    @Override
    public int szer() {
        return szer;
    }

    @Override
    public int wys() {
        return wys;
    }

    @Override
    public boolean pelnyKolor() {
        return true;
    }

    @Override
    public void wypisz(String tekst) {
    }

    @Override
    public Wejscie czytaj(long milisekundy) {
        return new Wejscie.Brak();
    }

    @Override
    public void close() {
    }
}
