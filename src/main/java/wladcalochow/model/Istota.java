package wladcalochow.model;

import java.io.Serializable;
import java.util.List;
import java.util.Random;

/**
 * Wspólna baza bohaterów i potworów.
 *
 * Zasada kopiowania: istoty są unikatowe. Nie ma konstruktora kopiującego ani clone();
 * kopiować wolno tylko kolekcje istot, nigdy same istoty.
 */
public abstract class Istota implements Serializable {

    /** Spadek morale po każdym udanym ataku, niezależny od zadanych obrażeń. */
    public static final int SPADEK_MORALE_PO_TRAFIENIU = 8;

    private final String imie;
    private int silaAtaku;
    private int punktyZycia;
    private int maxPunktyZycia;
    private int morale;
    private int maxMorale;
    private int zloto;
    private boolean ogluszona;
    private boolean uciekla;

    protected Istota(String imie, int silaAtaku, int maxPunktyZycia, int maxMorale, int zloto) {
        if (imie == null || imie.isBlank()) {
            throw new IllegalArgumentException("Istota musi mieć imię");
        }
        if (silaAtaku < 0 || maxPunktyZycia <= 0 || maxMorale <= 0 || zloto < 0) {
            throw new IllegalArgumentException("Niepoprawne statystyki istoty: " + imie);
        }
        this.imie = imie;
        this.silaAtaku = silaAtaku;
        this.maxPunktyZycia = maxPunktyZycia;
        this.punktyZycia = maxPunktyZycia;
        this.maxMorale = maxMorale;
        this.morale = maxMorale;
        this.zloto = zloto;
    }

    // ---- zachowanie w walce (polimorficzne) ----

    /** Nazwa rodzaju istoty, np. "Rycerz" albo "Goblin". */
    public abstract String nazwaRodzaju();

    /** Istoty atakujące na odległość działają w rundzie jako pierwsze. */
    public boolean atakujeNaOdleglosc() {
        return false;
    }

    /** Domyślnie istota ucieka, gdy jej morale spadnie do zera. */
    public boolean czyUcieka(PoleWalki pole) {
        return morale <= 0;
    }

    /** Domyślne zachowanie: atak na losowego przeciwnika. */
    public Akcja wybierzAkcje(PoleWalki pole, Random los) {
        List<Istota> cele = pole.przeciwnicy(this);
        if (cele.isEmpty()) {
            return new Akcja.Czekanie();
        }
        return new Akcja.Atak(cele.get(los.nextInt(cele.size())));
    }

    /** Dodatkowy efekt po udanym trafieniu (np. ogłuszenie przez Goblina). */
    public void poTrafieniu(Istota cel, Random los, List<Zdarzenie> zdarzenia) {
        // domyślnie brak efektu
    }

    /** O ile spada morale tej istoty po otrzymaniu trafienia. */
    protected int spadekMoralePoTrafieniu() {
        return SPADEK_MORALE_PO_TRAFIENIU;
    }

    /** Wywoływane przed każdą walką. */
    public void przygotujDoWalki() {
        ogluszona = false;
        uciekla = false;
    }

    /** Wywoływane po walce: istota, która uciekła z pola bitwy, wraca do siebie. */
    public void odpocznijPoWalce() {
        ogluszona = false;
        uciekla = false;
    }

    // ---- metody wspólne ----

    public void otrzymajAtak(int obrazenia) {
        punktyZycia = Math.max(0, punktyZycia - Math.max(0, obrazenia));
        morale = Math.max(0, morale - spadekMoralePoTrafieniu());
    }

    /** Rana niezwiązana z atakiem (np. pułapka): nie zabija i nie obniża morale. */
    public void zran(int obrazenia) {
        punktyZycia = Math.max(1, punktyZycia - Math.max(0, obrazenia));
    }

    /** Zabójca natychmiast zabiera złoto ofiary. Zwraca zabraną kwotę. */
    public int zabierzZloto(Istota ofiara) {
        int lup = ofiara.zloto;
        ofiara.zloto = 0;
        zloto += lup;
        return lup;
    }

    public void dodajZloto(int kwota) {
        zloto += Math.max(0, kwota);
    }

    public void oglusz() {
        ogluszona = true;
    }

    public void ocknijSie() {
        ogluszona = false;
    }

    public void uciekaj() {
        uciekla = true;
    }

    public void wylecz() {
        punktyZycia = maxPunktyZycia;
    }

    public void zmienMorale(int delta) {
        morale = Math.max(0, Math.min(maxMorale, morale + delta));
    }

    /** Trwałe wzmocnienie (awans weterana, skalowanie trudności). */
    protected void wzmocnij(double procent) {
        if (silaAtaku > 0) {
            silaAtaku = Math.max(silaAtaku + 1, (int) Math.round(silaAtaku * (1 + procent)));
        }
        int noweMax = Math.max(maxPunktyZycia + 1, (int) Math.round(maxPunktyZycia * (1 + procent)));
        punktyZycia += noweMax - maxPunktyZycia;
        maxPunktyZycia = noweMax;
    }

    public boolean czyZyje() {
        return punktyZycia > 0;
    }

    /** Żywa i nie uciekła, czyli nadal bierze udział w walce. */
    public boolean wWalce() {
        return czyZyje() && !uciekla;
    }

    public double procentZycia() {
        return (double) punktyZycia / maxPunktyZycia;
    }

    public double procentMorale() {
        return (double) morale / maxMorale;
    }

    public String getImie() {
        return imie;
    }

    public int getSilaAtaku() {
        return silaAtaku;
    }

    public int getPunktyZycia() {
        return punktyZycia;
    }

    public int getMaxPunktyZycia() {
        return maxPunktyZycia;
    }

    public int getMorale() {
        return morale;
    }

    public int getMaxMorale() {
        return maxMorale;
    }

    public int getZloto() {
        return zloto;
    }

    public boolean czyOgluszona() {
        return ogluszona;
    }

    public boolean czyUciekla() {
        return uciekla;
    }

    @Override
    public String toString() {
        return nazwaRodzaju() + " " + imie;
    }
}
