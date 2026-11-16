package wladcalochow.model;

/** Silny i twardy jak rycerz, ale mniej nieustraszony: ucieka też przy ciężkich ranach. */
public class Ork extends Potwor {

    public Ork(String imie, int silaAtaku, int maxPunktyZycia, int maxMorale, int zold) {
        super(imie, silaAtaku, maxPunktyZycia, maxMorale, zold);
    }

    @Override
    public String nazwaRodzaju() {
        return "Ork";
    }

    @Override
    public boolean czyUcieka(PoleWalki pole) {
        return getMorale() <= 0 || procentZycia() < 0.20;
    }
}
