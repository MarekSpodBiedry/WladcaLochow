package wladcalochow.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Mocno bije i strzela, więc zawsze działa pierwszy. Poluje na strzelających potworów. */
public class Lucznik extends Bohater {

    public Lucznik(String imie, int silaAtaku, int maxPunktyZycia, int maxMorale, int zloto) {
        super(imie, silaAtaku, maxPunktyZycia, maxMorale, zloto);
    }

    @Override
    public String nazwaRodzaju() {
        return "Łucznik";
    }

    @Override
    public boolean atakujeNaOdleglosc() {
        return true;
    }

    @Override
    public Akcja wybierzAkcje(PoleWalki pole, Random los) {
        if (procentZycia() < 0.20) {
            // Ciężko ranny: jedna próba kradzieży i ucieczka niezależnie od skutku.
            return new Akcja.ProbaKradziezy(0.10, true);
        }
        List<Istota> cele = pole.przeciwnicy(this);
        if (cele.isEmpty()) {
            return new Akcja.Czekanie();
        }
        List<Istota> dystansowi = cele.stream().filter(Istota::atakujeNaOdleglosc).toList();
        List<Istota> pula = dystansowi.isEmpty() ? cele : dystansowi;
        if (procentMorale() < 0.50) {
            return new Akcja.Atak(pula.get(los.nextInt(pula.size())));
        }
        return new Akcja.Atak(Collections.min(pula, Comparator.comparingInt(Istota::getPunktyZycia)));
    }
}
