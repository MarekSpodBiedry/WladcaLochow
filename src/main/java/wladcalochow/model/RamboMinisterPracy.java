package wladcalochow.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Zatrudnia najsilniejszych i rezygnuje, gdy nie stać go na kolejnego. Do kopalni wysyła najsłabszych. */
public class RamboMinisterPracy extends MinisterPracy {

    private static final long serialVersionUID = 1L;

    public RamboMinisterPracy(String imie, int pensja, int werbunek, double nadzorKopalni) {
        super(imie, pensja, werbunek, nadzorKopalni);
    }

    @Override
    public String nazwaRodzaju() {
        return "Rambo";
    }

    @Override
    public String opisStylu() {
        return "Zatrudnia najsilniejszych, do kopalni wysyła najsłabszych.";
    }

    @Override
    public List<Potwor> wybierzDoZatrudnienia(List<Potwor> kandydaci, int budzet) {
        List<Potwor> ranking = new ArrayList<>(kandydaci);
        ranking.sort(Comparator.comparingInt(Potwor::moc).reversed());
        List<Potwor> wybrani = new ArrayList<>();
        int pozostalo = budzet;
        for (Potwor kandydat : ranking) {
            if (kandydat.getZold() > pozostalo) {
                break;
            }
            pozostalo -= kandydat.getZold();
            wybrani.add(kandydat);
        }
        return wybrani;
    }

    @Override
    public List<Potwor> wybierzDoKopalni(List<Potwor> stan, Random los) {
        List<Potwor> ranking = new ArrayList<>(stan);
        ranking.sort(Comparator.comparingInt(Potwor::moc));
        return new ArrayList<>(ranking.subList(0, liczbaGornikow(stan.size())));
    }
}
