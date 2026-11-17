package wladcalochow.model;

import java.util.List;
import java.util.Random;

/** Chodzący czołg. Morale nie spada mu w walce, a raz wybrany cel bije do samego końca. */
public class Ogr extends Potwor {

    private static final long serialVersionUID = 1L;

    /** Nie traci morale w walce, więc jest wart więcej, niż mówi siła i życie. */
    @Override
    public double mnoznikWartosci() {
        return 1.09;
    }

    private Istota upatrzonyCel;

    public Ogr(String imie, int silaAtaku, int maxPunktyZycia, int maxMorale, int zold) {
        super(imie, silaAtaku, maxPunktyZycia, maxMorale, zold);
    }

    @Override
    public String nazwaRodzaju() {
        return "Ogr";
    }

    @Override
    protected int spadekMoralePoTrafieniu() {
        return 0;
    }

    @Override
    public void przygotujDoWalki() {
        super.przygotujDoWalki();
        upatrzonyCel = null;
    }

    @Override
    public Akcja wybierzAkcje(PoleWalki pole, Random los) {
        List<Istota> cele = pole.przeciwnicy(this);
        if (cele.isEmpty()) {
            return new Akcja.Czekanie();
        }
        if (upatrzonyCel == null || !cele.contains(upatrzonyCel)) {
            upatrzonyCel = cele.get(los.nextInt(cele.size()));
        }
        return new Akcja.Atak(upatrzonyCel);
    }

    public Istota getUpatrzonyCel() {
        return upatrzonyCel;
    }
}
