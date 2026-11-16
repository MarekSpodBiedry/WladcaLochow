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
