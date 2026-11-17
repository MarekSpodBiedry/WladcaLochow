package wladcalochow.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import wladcalochow.ui.Dialog.Kawalek;
import wladcalochow.ui.Dialog.Linia;

/**
 * Wspólny wygląd okien: krótkie etykiety szare, wartości jasne, złoto złote, zalety zielone, wady czerwone.
 * Okna składają z tych klocków tabele, zamiast pisać zdania.
 */
final class Wiersze {

    private Wiersze() {
    }

    static Kawalek k(String tekst, int kolor) {
        return Kawalek.k(tekst, kolor);
    }

    static Kawalek g(String tekst, int kolor) {
        return Kawalek.g(tekst, kolor);
    }

    static Kawalek slaby(String tekst) {
        return Kawalek.k(tekst, Paleta.TEKST_SLABY);
    }

    static List<Kawalek> komorka(Kawalek... kawalki) {
        return List.of(kawalki);
    }

    static List<Kawalek> komorka(String tekst, int kolor) {
        return List.of(Kawalek.k(tekst, kolor));
    }

    @SafeVarargs
    static List<List<Kawalek>> wiersz(List<Kawalek>... komorki) {
        return Arrays.asList(komorki);
    }

    /** Wiersz nagłówków kolumn (szary). */
    static List<List<Kawalek>> naglowki(String... nazwy) {
        List<List<Kawalek>> wynik = new ArrayList<>();
        for (String nazwa : nazwy) {
            wynik.add(komorka(nazwa, Paleta.TEKST_SLABY));
        }
        return wynik;
    }

    /** Ten sam wiersz, cały przygaszony (np. obecny minister do porównania). */
    static List<List<Kawalek>> przygas(List<List<Kawalek>> wiersz) {
        List<List<Kawalek>> wynik = new ArrayList<>();
        for (List<Kawalek> komorka : wiersz) {
            wynik.add(komorka.stream().map(k -> Kawalek.k(k.tekst().replace(" ▲", "").replace(" ▼", ""),
                    Paleta.TEKST_SLABY)).toList());
        }
        return wynik;
    }

    /**
     * Wartość porównana z obecną: zielona ze strzałką ▲, gdy lepsza; czerwona z ▼, gdy gorsza; zwykła, gdy równa.
     *
     * @param wiecejLepiej czy większa liczba jest lepsza (pensja: nie, zniżki: tak)
     */
    static List<Kawalek> porownaj(String tekst, double wartosc, double obecna, boolean wiecejLepiej) {
        if (Math.abs(wartosc - obecna) < 1e-9) {
            return komorka(tekst, Paleta.TEKST);
        }
        boolean lepiej = (wartosc > obecna) == wiecejLepiej;
        return komorka(Kawalek.k(tekst + (lepiej ? " ▲" : " ▼"), lepiej ? Paleta.ZIELEN : Paleta.CZERWIEN));
    }

    /** Pasek informacji na górze okien rozkazów: „Rozkazy 2   Złoto 203”. */
    static Linia pasekRozkazow(int rozkazy, int zloto) {
        return Linia.z(slaby("Rozkazy "), g(String.valueOf(rozkazy), Paleta.TEKST), slaby("    Złoto "),
                g(String.valueOf(zloto), Paleta.ZLOTO));
    }

    /** Nagłówek sekcji: złoty, pogrubiony, z opcjonalnym szarym dopiskiem. */
    static Dialog.Opcja sekcja(String nazwa, String dopisek) {
        return Dialog.Opcja.linia(dopisek == null || dopisek.isEmpty()
                ? Linia.z(g(nazwa, Paleta.ZLOTO))
                : Linia.z(g(nazwa, Paleta.ZLOTO), slaby("   " + dopisek)));
    }

    static Dialog.Opcja odstep() {
        return Dialog.Opcja.linia(Linia.pusta());
    }

    /** Linia opisu: pogrubiona etykieta w kolorze i zwykły tekst za nią. */
    static Linia opis(String etykieta, int kolor, String tekst) {
        return Linia.z(g(etykieta, kolor), k(tekst, Paleta.TEKST));
    }

    /** Pasek poziomu: ■■□□□ 2/5. */
    static List<Kawalek> poziom(int poziom, int maks) {
        return komorka(k("■".repeat(poziom), Paleta.ZLOTO), k("□".repeat(maks - poziom), Paleta.TEKST_SLABY),
                k(" " + poziom + "/" + maks, Paleta.TEKST));
    }

    /** Kolor wartości względem maksimum: zielony, złoty albo czerwony. */
    static int kolorUdzialu(int wartosc, int maks) {
        double u = maks <= 0 ? 1 : wartosc / (double) maks;
        return u >= 0.8 ? Paleta.ZIELEN : u >= 0.5 ? Paleta.ZLOTO : Paleta.CZERWIEN;
    }
}
