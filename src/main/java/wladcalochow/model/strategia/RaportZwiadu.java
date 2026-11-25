package wladcalochow.model.strategia;

import java.io.Serializable;

/**
 * Poranna, częściowa zapowiedź ataku: przybliżona liczebność, jeden pewny rodzaj, szacowana siła (do oceny
 * ataku) i widełki ceny łapówki. Prawdziwa cena zawsze mieści się w widełkach (zniżki ministra są wliczone),
 * więc gracz, który płaci tylko wtedy, gdy stać go na górną granicę, nigdy się nie przeliczy.
 * Zapisy sprzed widełek mają lapowkaOd i lapowkaDo równe 0: wtedy obowiązuje sam szacunek.
 */
public record RaportZwiadu(int minBohaterow, int maxBohaterow, String ujawnionyRodzaj, int szacowanaSila,
                           int lapowkaOd, int lapowkaDo) implements Serializable {

    /** Raport bez widełek ceny (samouczek, testy): cena równa szacunkowi. */
    public RaportZwiadu(int minBohaterow, int maxBohaterow, String ujawnionyRodzaj, int szacowanaSila) {
        this(minBohaterow, maxBohaterow, ujawnionyRodzaj, szacowanaSila, szacowanaSila, szacowanaSila);
    }

    public boolean spokoj() {
        return maxBohaterow == 0;
    }

    /** Najniższa możliwa cena łapówki według zwiadu. */
    public int lapowkaMin() {
        return lapowkaOd > 0 ? lapowkaOd : szacowanaSila;
    }

    /** Najwyższa możliwa cena łapówki: tyle trzeba mieć, żeby łapówka na pewno się udała. */
    public int lapowkaMax() {
        return lapowkaDo > 0 ? lapowkaDo : szacowanaSila;
    }

    /** Widełki ceny do wyświetlenia, np. „830–1250” (jedna liczba, gdy granice są równe). */
    public String widelki() {
        return lapowkaMin() == lapowkaMax() ? String.valueOf(lapowkaMax()) : lapowkaMin() + "–" + lapowkaMax();
    }

    public String opis() {
        if (spokoj()) {
            return "Zwiad: okolica spokojna, nikt się nie zbliża.";
        }
        String liczba = minBohaterow == maxBohaterow
                ? String.valueOf(minBohaterow)
                : minBohaterow + "-" + maxBohaterow;
        String rodzaj = ujawnionyRodzaj == null ? "" : " Wśród nich na pewno: " + ujawnionyRodzaj + ".";
        String lapowka = lapowkaMax() <= 0 ? ""
                : " Przekupienie ich kosztowałoby od " + lapowkaMin() + " do " + lapowkaMax() + " złota.";
        return "Zwiad: spodziewana liczba bohaterów: " + liczba + "." + rodzaj + lapowka;
    }
}
