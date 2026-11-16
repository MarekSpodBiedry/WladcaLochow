package wladcalochow.model.strategia;

import java.io.Serializable;

/** Poranna, częściowa zapowiedź ataku: przybliżona liczebność, jeden pewny rodzaj i szacowana siła. */
public record RaportZwiadu(int minBohaterow, int maxBohaterow, String ujawnionyRodzaj, int szacowanaSila)
        implements Serializable {

    public boolean spokoj() {
        return maxBohaterow == 0;
    }

    public String opis() {
        if (spokoj()) {
            return "Zwiad: okolica spokojna, nikt się nie zbliża.";
        }
        String liczba = minBohaterow == maxBohaterow
                ? String.valueOf(minBohaterow)
                : minBohaterow + "-" + maxBohaterow;
        String rodzaj = ujawnionyRodzaj == null ? "" : " Wśród nich na pewno: " + ujawnionyRodzaj + ".";
        String lapowka = szacowanaSila <= 0 ? ""
                : " Przekupienie ich kosztowałoby około " + szacowanaSila + " złota.";
        return "Zwiad: spodziewana liczba bohaterów: " + liczba + "." + rodzaj + lapowka;
    }
}
