package wladcalochow.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Rozporządza budżetem i wypłaca żołd. Podklasy różnią się tym, ile oddają na rekrutację. */
public abstract class MinisterFinansow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Wynik wieczornej wypłaty żołdu. */
    public record WynikWyplaty(int wyplacono, List<Potwor> oplaceni, List<Potwor> nieoplaceni,
                               List<Potwor> odeszli) {
    }

    private final String imie;
    private final int pensja;
    private final double negocjacje;

    protected MinisterFinansow(String imie, int pensja, double negocjacje) {
        if (pensja < 0 || negocjacje < 0 || negocjacje > 0.9) {
            throw new IllegalArgumentException("Niepoprawne statystyki ministra: " + imie);
        }
        this.imie = imie;
        this.pensja = pensja;
        this.negocjacje = negocjacje;
    }

    public abstract String nazwaRodzaju();

    public abstract String opisStylu();

    /** Ile złota minister jest gotów przekazać Ministrowi Pracy na rekrutację. */
    public abstract int kwotaNaRekrutacje(int zlotoWSkarbcu, int dziennyKosztZoldu);

    /** Cena ulepszenia, łapówki lub opłaty po zniżce wynegocjowanej przez ministra. */
    public int cenaPoNegocjacjach(int cena) {
        return Math.max(1, (int) Math.round(cena * (1 - negocjacje)));
    }

    /**
     * Wypłaca żołd w dowolnej (losowej) kolejności. Potwór dostaje całość brakującej kwoty albo nic:
     * bez żołdu traci morale, a przy morale poniżej 10% odchodzi z lochu.
     */
    public WynikWyplaty wyplacZold(Skarbiec skarbiec, List<Potwor> uprawnieni, Random los) {
        List<Potwor> kolejnosc = new ArrayList<>(uprawnieni);
        Collections.shuffle(kolejnosc, los);
        List<Potwor> oplaceni = new ArrayList<>();
        List<Potwor> nieoplaceni = new ArrayList<>();
        List<Potwor> odeszli = new ArrayList<>();
        int suma = 0;
        for (Potwor potwor : kolejnosc) {
            int nalezne = potwor.nalezneDzis();
            if (skarbiec.wyplac(nalezne)) {
                suma += nalezne;
                potwor.otrzymajZold();
                oplaceni.add(potwor);
            } else if (potwor.chceOdejsc()) {
                odeszli.add(potwor);
            } else {
                potwor.brakZoldu();
                nieoplaceni.add(potwor);
            }
        }
        return new WynikWyplaty(suma, oplaceni, nieoplaceni, odeszli);
    }

    /** Jaką część dziennego przychodu lochu każdy minister dolicza do stałej pensji. */
    public static final double UDZIAL_W_PRZYCHODZIE = 0.03;
    /** Ile dodatkowo bierze minister chciwy. */
    public static final double UDZIAL_CHCIWEGO = 0.05;

    private wladcalochow.model.strategia.CechaMinistra cecha;
    private wladcalochow.model.strategia.WadaMinistra wada;

    /** Nadaje ministrowi cechę i wadę. Zwraca tego samego ministra. */
    public MinisterFinansow zCecha(wladcalochow.model.strategia.CechaMinistra cecha,
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
            return 0;  // darmowy stażysta
        }
        double udzial = UDZIAL_W_PRZYCHODZIE + (ma(wladcalochow.model.strategia.WadaMinistra.CHCIWY) ? UDZIAL_CHCIWEGO : 0);
        return pensja + (int) Math.round(Math.max(0, przychodDnia) * udzial);
    }

    public String getImie() {
        return imie;
    }

    /** Darmowy stażysta (zastępstwo po ministrze, który odszedł): bez pensji, ale czasem coś pomyli. */
    public boolean czyStazysta() {
        return pensja == 0;
    }

    public int getPensja() {
        return pensja;
    }

    public double getNegocjacje() {
        return negocjacje;
    }

    @Override
    public String toString() {
        return imie + " (" + nazwaRodzaju() + ")";
    }
}
