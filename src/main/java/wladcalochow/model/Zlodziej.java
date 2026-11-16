package wladcalochow.model;

import java.util.Random;

/** Nie walczy (siła ataku 0), tylko próbuje okraść skarbiec. Płochliwy. */
public class Zlodziej extends Bohater {

    private static final long serialVersionUID = 1L;

    public static final double SZANSA_KRADZIEZY = 0.20;

    public Zlodziej(String imie, int maxPunktyZycia, int maxMorale, int zloto) {
        super(imie, 0, maxPunktyZycia, maxMorale, zloto);
    }

    @Override
    public String nazwaRodzaju() {
        return "Złodziej";
    }

    @Override
    public boolean czyUcieka(PoleWalki pole) {
        if (getMorale() <= 0) {
            return true;
        }
        double prog = 0.20 * getPunktyZycia();
        return pole.przeciwnicy(this).stream().anyMatch(p -> p.getSilaAtaku() >= prog);
    }

    @Override
    public Akcja wybierzAkcje(PoleWalki pole, Random los) {
        return new Akcja.ProbaKradziezy(SZANSA_KRADZIEZY, false);
    }
}
