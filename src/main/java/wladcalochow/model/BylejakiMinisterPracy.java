package wladcalochow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Zatrudnia jak leci z listy i stara się wydać jak najwięcej. Do kopalni wysyła losowych. */
public class BylejakiMinisterPracy extends MinisterPracy {

    private static final long serialVersionUID = 1L;

    public BylejakiMinisterPracy(String imie, int pensja, int werbunek, double nadzorKopalni) {
        super(imie, pensja, werbunek, nadzorKopalni);
    }

    @Override
    public String nazwaRodzaju() {
        return "Bylejaki";
    }

    @Override
    public String opisStylu() {
        return "Zatrudnia jak leci z listy, do kopalni wysyła losowych.";
    }

    @Override
    public List<Potwor> wybierzDoZatrudnienia(List<Potwor> kandydaci, int budzet) {
        List<Potwor> wybrani = new ArrayList<>();
        int pozostalo = budzet;
        for (Potwor kandydat : kandydaci) {
            if (kandydat.getZold() <= pozostalo) {
                pozostalo -= kandydat.getZold();
                wybrani.add(kandydat);
            }
        }
        return wybrani;
    }

    @Override
    public List<Potwor> wybierzDoKopalni(List<Potwor> stan, Random los) {
        List<Potwor> tasowani = new ArrayList<>(stan);
        Collections.shuffle(tasowani, los);
        return new ArrayList<>(tasowani.subList(0, liczbaGornikow(stan.size())));
    }
}
