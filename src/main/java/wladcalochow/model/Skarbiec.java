package wladcalochow.model;

import java.io.Serializable;

/** Budżet lochu. Pilnuje, żeby złoto nigdy nie zeszło poniżej zera. */
public class Skarbiec implements Serializable {

    private static final long serialVersionUID = 1L;

    private int zloto;

    public Skarbiec(int zloto) {
        if (zloto < 0) {
            throw new IllegalArgumentException("Skarbiec nie może mieć ujemnego złota");
        }
        this.zloto = zloto;
    }

    public void wplac(int kwota) {
        zloto += Math.max(0, kwota);
    }

    /** Wypłaca całą kwotę albo nic. Zwraca true, jeśli wypłata się udała. */
    public boolean wyplac(int kwota) {
        if (kwota < 0 || kwota > zloto) {
            return false;
        }
        zloto -= kwota;
        return true;
    }

    /** Zabiera najwyżej podaną kwotę (kradzież, plądrowanie). Zwraca faktycznie zabraną. */
    public int zabierz(int maksymalnie) {
        int kwota = Math.max(0, Math.min(maksymalnie, zloto));
        zloto -= kwota;
        return kwota;
    }

    public int getZloto() {
        return zloto;
    }

    public boolean czyPusty() {
        return zloto == 0;
    }
}
