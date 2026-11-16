package wladcalochow.model;

/**
 * Trzyma rezerwę na dwa dni żołdu i dopiero nadwyżkę oddaje na rekrutację. Przy rezerwie na trzy dni
 * (jak w pierwotnym opisie gry) loch prawie nigdy jej nie uzbierał, więc minister nikogo nie zatrudniał.
 */
public class ZachowawczyMinisterFinansow extends MinisterFinansow {

    public static final int DNI_REZERWY = 2;

    public ZachowawczyMinisterFinansow(String imie, int pensja, double negocjacje) {
        super(imie, pensja, negocjacje);
    }

    @Override
    public String nazwaRodzaju() {
        return "Zachowawczy";
    }

    @Override
    public String opisStylu() {
        return "Trzyma rezerwę na " + DNI_REZERWY + " dni żołdu, na rekrutację oddaje tylko nadwyżkę.";
    }

    @Override
    public int kwotaNaRekrutacje(int zlotoWSkarbcu, int dziennyKosztZoldu) {
        return Math.max(0, zlotoWSkarbcu - DNI_REZERWY * dziennyKosztZoldu);
    }
}
