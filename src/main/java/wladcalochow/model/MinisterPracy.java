package wladcalochow.model;

import java.io.Serializable;
import java.util.List;
import java.util.Random;

/** Zatrudnia nowe potwory i deleguje część załogi do kopania złota. */
public abstract class MinisterPracy implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String imie;
    private final int pensja;
    private final int werbunek;
    private final double nadzorKopalni;

    protected MinisterPracy(String imie, int pensja, int werbunek, double nadzorKopalni) {
        if (pensja < 0 || werbunek < 0 || nadzorKopalni < 0) {
            throw new IllegalArgumentException("Niepoprawne statystyki ministra: " + imie);
        }
        this.imie = imie;
        this.pensja = pensja;
        this.werbunek = werbunek;
        this.nadzorKopalni = nadzorKopalni;
    }

    public abstract String nazwaRodzaju();

    public abstract String opisStylu();

    /** Wybiera z listy kandydatów tych, których zatrudnia w ramach budżetu. Koszt = żołd potwora. */
    public abstract List<Potwor> wybierzDoZatrudnienia(List<Potwor> kandydaci, int budzet);

    /** Wybiera potwory, które dziś kopią złoto zamiast walczyć. */
    public abstract List<Potwor> wybierzDoKopalni(List<Potwor> stan, Random los);

    /** Do kopalni trafia jedna trzecia stanu osobowego lochu. */
    protected int liczbaGornikow(int stanOsobowy) {
        return stanOsobowy / 3;
    }

    private wladcalochow.model.strategia.CechaMinistra cecha;
    private wladcalochow.model.strategia.WadaMinistra wada;

    /** Nadaje ministrowi cechę i wadę. Zwraca tego samego ministra. */
    public MinisterPracy zCecha(wladcalochow.model.strategia.CechaMinistra cecha,
                                wladcalochow.model.strategia.WadaMinistra wada) {
        this.cecha = cecha;
        this.wada = wada;
        return this;
    }

    public wladcalochow.model.strategia.CechaMinistra getCecha() {
        return cecha;
    }

    public wladcalochow.model.strategia.WadaMinistra getWada() {
        return wada;
    }

    public boolean ma(wladcalochow.model.strategia.CechaMinistra c) {
        return cecha == c;
    }

    public boolean ma(wladcalochow.model.strategia.WadaMinistra w) {
        return wada == w;
    }

    /** Pensja na dziś: stała część plus udział w dzisiejszym przychodzie (większy u chciwego). */
    public int pensjaDnia(int przychodDnia) {
        if (pensja == 0) {
            return 0;  // darmowa stażystka
        }
        double udzial = MinisterFinansow.UDZIAL_W_PRZYCHODZIE
                + (ma(wladcalochow.model.strategia.WadaMinistra.CHCIWY) ? MinisterFinansow.UDZIAL_CHCIWEGO : 0);
        return pensja + (int) Math.round(Math.max(0, przychodDnia) * udzial);
    }

    public String getImie() {
        return imie;
    }

    public int getPensja() {
        return pensja;
    }

    /** Ilu dodatkowych kandydatów dziennie przyciąga minister. */
    public int getWerbunek() {
        return werbunek;
    }

    /** Premia do wydobycia w kopalni (ułamek, np. 0.10 = +10%). */
    public double getNadzorKopalni() {
        return nadzorKopalni;
    }

    @Override
    public String toString() {
        return imie + " (" + nazwaRodzaju() + ")";
    }
}
