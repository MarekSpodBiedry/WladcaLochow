package wladcalochow.model;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Jedna runda walki: każda istota działa najwyżej raz.
 * Atakujący na odległość są wyciągani jako pierwsi, pozostali w losowej kolejności.
 */
public class RundaWalki implements Serializable {

    private final int numer;
    private final Deque<Istota> kolejka;

    public RundaWalki(int numer, List<? extends Istota> uczestnicy, Random los) {
        this.numer = numer;
        List<Istota> dystansowi = new ArrayList<>();
        List<Istota> pozostali = new ArrayList<>();
        for (Istota uczestnik : uczestnicy) {
            (uczestnik.atakujeNaOdleglosc() ? dystansowi : pozostali).add(uczestnik);
        }
        Collections.shuffle(dystansowi, los);
        Collections.shuffle(pozostali, los);
        kolejka = new ArrayDeque<>(dystansowi);
        kolejka.addAll(pozostali);
    }

    /** Następna istota, która nadal bierze udział w walce, albo null, gdy runda się skończyła. */
    public Istota nastepny() {
        while (!kolejka.isEmpty()) {
            Istota istota = kolejka.poll();
            if (istota.wWalce()) {
                return istota;
            }
        }
        return null;
    }

    public boolean czyZakonczona() {
        return kolejka.stream().noneMatch(Istota::wWalce);
    }

    public int getNumer() {
        return numer;
    }

    /** Kolejność istot, które jeszcze nie działały w tej rundzie. */
    public List<Istota> oczekujacy() {
        return new ArrayList<>(kolejka);
    }
}
