package wladcalochow.model;

/** Silny i twardy, niemal nieustraszony. Nie kradnie, walczy dopóki ma z kim. */
public class Rycerz extends Bohater {

    private static final long serialVersionUID = 1L;

    /** Wycena z pojedynków: prawie dokładnie tyle, ile mówi siła i życie. */
    @Override
    public double mnoznikWartosci() {
        return 0.95;
    }

    public Rycerz(String imie, int silaAtaku, int maxPunktyZycia, int maxMorale, int zloto) {
        super(imie, silaAtaku, maxPunktyZycia, maxMorale, zloto);
    }

    @Override
    public String nazwaRodzaju() {
        return "Rycerz";
    }

    @Override
    public boolean czyUcieka(PoleWalki pole) {
        return getMorale() <= 0 || procentZycia() < 0.05;
    }
}
