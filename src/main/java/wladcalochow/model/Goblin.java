package wladcalochow.model;

import java.util.List;
import java.util.Random;

/** Strachliwy i mało wytrzymały, ale strzela z procy i potrafi ogłuszyć cel. */
public class Goblin extends Potwor {

    public static final double SZANSA_OGLUSZENIA = 0.30;

    public Goblin(String imie, int silaAtaku, int maxPunktyZycia, int maxMorale, int zold) {
        super(imie, silaAtaku, maxPunktyZycia, maxMorale, zold);
    }

    @Override
    public String nazwaRodzaju() {
        return "Goblin";
    }

    @Override
    public boolean atakujeNaOdleglosc() {
        return true;
    }

    @Override
    public void poTrafieniu(Istota cel, Random los, List<Zdarzenie> zdarzenia) {
        if (cel.czyZyje() && los.nextDouble() < SZANSA_OGLUSZENIA) {
            cel.oglusz();
            zdarzenia.add(new Zdarzenie.Ogluszenie(cel));
        }
    }
}
