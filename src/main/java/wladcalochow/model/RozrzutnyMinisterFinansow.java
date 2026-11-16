package wladcalochow.model;

/** Zostawia tylko tyle, ile trzeba na dzisiejszy żołd, a resztę oddaje na rekrutację. */
public class RozrzutnyMinisterFinansow extends MinisterFinansow {

    private static final long serialVersionUID = 1L;

    public RozrzutnyMinisterFinansow(String imie, int pensja, double negocjacje) {
        super(imie, pensja, negocjacje);
    }

    @Override
    public String nazwaRodzaju() {
        return "Rozrzutny";
    }

    @Override
    public String opisStylu() {
        return "Zostawia złoto tylko na dzisiejszy żołd, resztę wydaje na rekrutację.";
    }

    @Override
    public int kwotaNaRekrutacje(int zlotoWSkarbcu, int dziennyKosztZoldu) {
        return Math.max(0, zlotoWSkarbcu - dziennyKosztZoldu);
    }
}
