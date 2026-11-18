package wladcalochow.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Okno wyboru na środku ekranu. Opcje wybiera się strzałkami i Enterem, cyfrą albo kliknięciem.
 *
 * <p>Opcja to albo zwykły tytuł z opisem (zawijany), albo wiersz tabeli: komórki z kolorowymi kawałkami
 * tekstu, wyrównane w kolumny razem z innymi wierszami tej samej tabeli. Wiersze tylko do czytania (nagłówek
 * sekcji, nagłówek kolumn, obecny stan do porównania) nie dają się zaznaczyć. Zaznaczona opcja może mieć
 * dłuższy opis, który pojawia się w panelu na dole okna: dzięki temu lista zostaje krótka, a szczegóły są
 * tylko tam, gdzie gracz właśnie patrzy.
 *
 * <p>Tekst nigdy nie jest ucinany: akapity i opisy zawijają się, a gdy tabela nie mieści się w szerokości,
 * znikają najpierw kolumny oznaczone jako zbędne ({@link #ukrywaj}).
 */
final class Dialog {

    /** Kawałek tekstu w jednym kolorze. */
    record Kawalek(String tekst, int kolor, boolean gruby) {
        static Kawalek k(String tekst, int kolor) {
            return new Kawalek(tekst, kolor, false);
        }

        static Kawalek g(String tekst, int kolor) {
            return new Kawalek(tekst, kolor, true);
        }
    }

    /** Wiersz tekstu złożony z kolorowych kawałków. */
    record Linia(List<Kawalek> kawalki) {
        static Linia z(Kawalek... kawalki) {
            return new Linia(List.of(kawalki));
        }

        static Linia tekst(String tekst, int kolor) {
            return z(Kawalek.k(tekst, kolor));
        }

        static Linia pusta() {
            return new Linia(List.of());
        }

        int szerokosc() {
            int suma = 0;
            for (Kawalek kawalek : kawalki) {
                suma += Ekran.szerokosc(kawalek.tekst());
            }
            return suma;
        }
    }

    /**
     * Pozycja w oknie.
     *
     * @param wybieralna czy da się ją zaznaczyć (nagłówki i wiersze do porównania nie)
     * @param tabela     numer tabeli, z której wierszami wyrównuje kolumny; ujemny: bez kolumn
     * @param komorki    komórki wiersza (null: zwykła opcja z etykietą i szczegółem)
     * @param opis       dłuższy opis, pokazywany w panelu na dole, gdy opcja jest zaznaczona
     * @param znacznik   krótki napis (do 5 znaków) w miejscu numeru, np. „teraz”
     * @param ramki      małe ramki obok siebie (np. polegli, awanse), nie do wybrania; null: brak
     */
    record Opcja(String etykieta, String szczegol, boolean aktywna, Runnable akcja, boolean wybieralna, int tabela,
                 List<List<Kawalek>> komorki, List<Linia> opis, String znacznik, List<Ramka> ramki) {

        Opcja(String etykieta, String szczegol, boolean aktywna, Runnable akcja) {
            this(etykieta, szczegol, aktywna, akcja, true, -1, null, List.of(), null, null);
        }

        /** Wiersz tabeli do wybrania. */
        static Opcja wiersz(int tabela, List<List<Kawalek>> komorki, List<Linia> opis, boolean aktywna,
                            Runnable akcja) {
            return new Opcja("", "", aktywna, akcja, true, tabela, komorki, opis, null, null);
        }

        /** Wiersz tabeli tylko do czytania: nagłówek kolumn albo obecny stan do porównania. */
        static Opcja info(int tabela, List<List<Kawalek>> komorki, String znacznik) {
            return new Opcja("", "", false, () -> {
            }, false, tabela, komorki, List.of(), znacznik, null);
        }

        /** Jedna linia bez kolumn, nie do wybrania: nagłówek sekcji albo odstęp. */
        static Opcja linia(Linia linia) {
            return new Opcja("", "", false, () -> {
            }, false, -1, List.of(linia.kawalki()), List.of(), null, null);
        }

        /** Zwykła opcja (tytuł zawijany) z opisem w panelu na dole. */
        static Opcja zOpisem(String etykieta, List<Linia> opis, boolean aktywna, Runnable akcja) {
            return new Opcja(etykieta, "", aktywna, akcja, true, -1, null, opis, null, null);
        }

        /** Rząd małych ramek obok siebie; gdy się nie mieszczą, przechodzą do następnego rzędu. */
        static Opcja ramki(List<Ramka> ramki) {
            return new Opcja("", "", false, () -> {
            }, false, -1, null, List.of(), null, ramki);
        }
    }

    /** Mała ramka z tytułem w kolorze i krótką listą w środku (np. „Polegli”: imiona). */
    record Ramka(String tytul, int kolor, List<String> pozycje) {
    }

    /** Najszersza treść ramki: dłuższe listy zawijają się w kilka linii. */
    private static final int SZEROKOSC_RAMKI = 28;

    /** Rysuje ramki obok siebie jako gotowe linie tekstu, przenosząc je do kolejnego rzędu, gdy brak miejsca. */
    static List<Linia> ramkiJakoLinie(List<Ramka> ramki, int dostepne) {
        List<List<Linia>> rysunki = new ArrayList<>();
        List<Integer> szerokosci = new ArrayList<>();
        for (Ramka r : ramki) {
            int maks = Math.min(SZEROKOSC_RAMKI, Math.max(8, dostepne - 4));
            List<String> tresc = Ekran.zawin(String.join(", ", r.pozycje()), maks);
            int wnetrze = Ekran.szerokosc(r.tytul()) + 2;
            for (String s : tresc) {
                wnetrze = Math.max(wnetrze, Ekran.szerokosc(s));
            }
            int szer = wnetrze + 4;
            List<Linia> rysunek = new ArrayList<>();
            rysunek.add(Linia.z(Kawalek.k("╭─ ", r.kolor()), Kawalek.g(r.tytul(), r.kolor()),
                    Kawalek.k(" " + "─".repeat(szer - 5 - Ekran.szerokosc(r.tytul())) + "╮", r.kolor())));
            for (String s : tresc) {
                rysunek.add(Linia.z(Kawalek.k("│ ", r.kolor()), Kawalek.k(Ekran.dopelnij(s, wnetrze), Paleta.TEKST),
                        Kawalek.k(" │", r.kolor())));
            }
            rysunek.add(Linia.z(Kawalek.k("╰" + "─".repeat(szer - 2) + "╯", r.kolor())));
            rysunki.add(rysunek);
            szerokosci.add(szer);
        }
        List<Linia> wynik = new ArrayList<>();
        int i = 0;
        while (i < rysunki.size()) {
            // Ile ramek zmieści się w tym rzędzie (co najmniej jedna).
            int j = i;
            int zajete = 0;
            while (j < rysunki.size() && (j == i || zajete + 1 + szerokosci.get(j) <= dostepne)) {
                zajete += (j == i ? 0 : 1) + szerokosci.get(j);
                j++;
            }
            int wysokosc = 0;
            for (int k = i; k < j; k++) {
                wysokosc = Math.max(wysokosc, rysunki.get(k).size());
            }
            for (int w = 0; w < wysokosc; w++) {
                List<Kawalek> linia = new ArrayList<>();
                for (int k = i; k < j; k++) {
                    if (k > i) {
                        linia.add(Kawalek.k(" ", Paleta.TEKST));
                    }
                    List<Linia> rysunek = rysunki.get(k);
                    if (w < rysunek.size()) {
                        linia.addAll(rysunek.get(w).kawalki());
                    } else {
                        linia.add(Kawalek.k(" ".repeat(szerokosci.get(k)), Paleta.TEKST));
                    }
                }
                wynik.add(new Linia(linia));
            }
            i = j;
        }
        return wynik;
    }

    /** Akapit wstępu w wybranym kolorze. Pusty tekst daje odstęp. */
    record Wiersz(String tekst, int kolor) {
    }

    /** Odbiorca stref klikalnych wyznaczanych podczas rysowania. */
    interface Strefy {
        void dodaj(int x, int y, int w, int h, Runnable akcja);
    }

    private static final int SZEROKOSC = 78;
    private static final int MARGINES_EKRANU = 6;
    private static final int ODSTEP_KOLUMN = 2;

    private final String tytul;
    private final List<Linia> wstep;
    private final List<Opcja> opcje;
    private final Runnable anuluj;
    /** Kolumny, które można ukryć, gdy tabela się nie mieści (w kolejności ukrywania). */
    private final Map<Integer, List<Integer>> ukrywane = new HashMap<>();
    private int szerokoscDocelowa = SZEROKOSC;
    private int wybrana;
    private int pierwsza;
    private int widoczne = 1;
    /** Gdy gra toczy się sama, takie okno zatwierdza się samo po chwili (pierwszą aktywną opcją). */
    private boolean samoZatwierdzane;

    Dialog(String tytul, List<String> wstep, List<Opcja> opcje, Runnable anuluj) {
        this(tytul, wstep.stream().map(tekst -> Linia.tekst(tekst, Paleta.TEKST)).toList(), anuluj, opcje);
    }

    /** Wariant z kolorowymi akapitami (kolejność parametrów odróżnia go od wariantu z samym tekstem). */
    Dialog(List<Wiersz> wstep, String tytul, List<Opcja> opcje, Runnable anuluj) {
        this(tytul, wstep.stream().map(w -> Linia.tekst(w.tekst(), w.kolor())).toList(), anuluj, opcje);
    }

    /** Wariant z wstępem z kolorowych kawałków. */
    static Dialog zLiniami(String tytul, List<Linia> wstep, List<Opcja> opcje, Runnable anuluj) {
        return new Dialog(tytul, wstep, anuluj, opcje);
    }

    private Dialog(String tytul, List<Linia> wstep, Runnable anuluj, List<Opcja> opcje) {
        this.tytul = tytul;
        this.wstep = wstep;
        this.opcje = opcje;
        this.anuluj = anuluj;
        wybrana = -1;
        for (int i = 0; i < opcje.size() && wybrana < 0; i++) {
            if (opcje.get(i).wybieralna() && opcje.get(i).aktywna()) {
                wybrana = i;
            }
        }
        for (int i = 0; i < opcje.size() && wybrana < 0; i++) {
            if (opcje.get(i).wybieralna()) {
                wybrana = i;
            }
        }
        wybrana = Math.max(0, wybrana);
    }

    /** Kolumny tej tabeli, które znikają (w podanej kolejności), gdy okno jest za wąskie. Zwraca to samo okno. */
    Dialog ukrywaj(int tabela, Integer... kolumny) {
        ukrywane.put(tabela, List.of(kolumny));
        return this;
    }

    /** Szerokość, do której okno się poszerza, jeśli ekran pozwala (domyślnie 78). Zwraca to samo okno. */
    Dialog szerokosc(int szerokosc) {
        szerokoscDocelowa = szerokosc;
        return this;
    }

    /** Oznacza okno jako zatwierdzane samoczynnie w trybie, w którym gra toczy się sama. Zwraca to samo okno. */
    Dialog samoZatwierdzane() {
        samoZatwierdzane = true;
        return this;
    }

    boolean czySamoZatwierdzane() {
        return samoZatwierdzane;
    }

    /** Miejsca wybieralnych opcji z ostatniej klatki: x, y, szerokość, wysokość, numer opcji. */
    private final List<int[]> miejscaOpcji = new ArrayList<>();

    /** Myszka nad opcją: zaznacza ją (podświetlenie i opis na dole), tak jak strzałki. */
    void najedz(int x, int y) {
        for (int[] m : miejscaOpcji) {
            if (x >= m[0] && x < m[0] + m[2] && y >= m[1] && y < m[1] + m[3]) {
                wybrana = m[4];
                return;
            }
        }
    }

    /** Kółko myszy: przesuwa zaznaczenie o jedną opcję w górę (-1) albo w dół (+1). */
    void przewin(int kierunek) {
        wybrana = nastepna(wybrana, kierunek < 0 ? -1 : 1);
    }

    /** Wybiera zaznaczoną opcję, tak jakby gracz nacisnął Enter. */
    void zatwierdz() {
        wybierz(wybrana);
    }

    // ---- zawijanie kolorowego tekstu ----

    /** Zawija linię z kolorowymi kawałkami do podanej szerokości, łamiąc na spacjach. */
    static List<Linia> zawin(Linia linia, int maks) {
        List<Linia> wynik = new ArrayList<>();
        List<Kawalek> biezaca = new ArrayList<>();
        int szer = 0;
        for (Kawalek kawalek : linia.kawalki()) {
            String reszta = kawalek.tekst();
            while (!reszta.isEmpty()) {
                // Następne słowo razem ze spacją za nim.
                int spacja = reszta.indexOf(' ');
                String slowo = spacja < 0 ? reszta : reszta.substring(0, spacja + 1);
                reszta = reszta.substring(slowo.length());
                int s = Ekran.szerokosc(slowo.stripTrailing());
                if (szer > 0 && szer + s > maks) {
                    wynik.add(new Linia(przytnijKoniec(biezaca)));
                    biezaca = new ArrayList<>();
                    szer = 0;
                }
                while (Ekran.szerokosc(slowo.stripTrailing()) > maks) {
                    // Słowo dłuższe niż cała linia: tniemy je na twardo.
                    String kawal = Ekran.przytnijBezKropek(slowo, maks);
                    wynik.add(Linia.z(new Kawalek(kawal, kawalek.kolor(), kawalek.gruby())));
                    slowo = slowo.substring(kawal.length());
                }
                biezaca.add(new Kawalek(slowo, kawalek.kolor(), kawalek.gruby()));
                szer += Ekran.szerokosc(slowo);
            }
        }
        if (!biezaca.isEmpty() || wynik.isEmpty()) {
            wynik.add(new Linia(przytnijKoniec(biezaca)));
        }
        return wynik;
    }

    private static List<Kawalek> przytnijKoniec(List<Kawalek> kawalki) {
        List<Kawalek> wynik = new ArrayList<>(kawalki);
        if (!wynik.isEmpty()) {
            Kawalek ostatni = wynik.get(wynik.size() - 1);
            wynik.set(wynik.size() - 1, new Kawalek(ostatni.tekst().stripTrailing(), ostatni.kolor(), ostatni.gruby()));
        }
        return wynik;
    }

    // ---- układ ----

    /** Rozłożenie treści okna dla konkretnej szerokości. */
    private final class Uklad {
        final int w;
        final List<Linia> wiersze = new ArrayList<>();
        /** Gotowe linie każdej opcji (tytuł i szczegół albo wiersz tabeli). */
        final List<List<Linia>> linie = new ArrayList<>();
        final List<List<Linia>> opisy = new ArrayList<>();
        int panel;

        Uklad(int w) {
            this.w = w;
            int tekst = w - 9;
            for (Linia akapit : wstep) {
                wiersze.addAll(zawin(akapit, w - 4));
            }
            // Tabela zaczyna się kolumnę dalej niż zwykłe opcje (miejsce na znacznik „teraz”).
            int naTabele = tekst - 1;
            Map<Integer, int[]> szerokosci = szerokosciKolumn(naTabele);
            for (Opcja opcja : opcje) {
                List<Linia> l = new ArrayList<>();
                if (opcja.ramki() != null) {
                    l.addAll(ramkiJakoLinie(opcja.ramki(), w - 4));
                } else if (opcja.komorki() == null) {
                    for (String s : Ekran.zawin(opcja.etykieta(), tekst)) {
                        l.add(Linia.z(new Kawalek(s, opcja.aktywna() ? Paleta.TEKST : Paleta.TEKST_SLABY,
                                opcja.aktywna())));
                    }
                    if (!opcja.szczegol().isEmpty()) {
                        for (String s : Ekran.zawin(opcja.szczegol(), tekst)) {
                            l.add(Linia.tekst(s, Paleta.TEKST_SLABY));
                        }
                    }
                } else if (opcja.tabela() < 0) {
                    List<Kawalek> razem = new ArrayList<>();
                    opcja.komorki().forEach(razem::addAll);
                    l.addAll(zawin(new Linia(razem), w - 4));
                } else {
                    l.addAll(wierszTabeli(opcja, szerokosci.get(opcja.tabela()), naTabele));
                }
                linie.add(l);
                List<Linia> opis = new ArrayList<>();
                for (Linia linia : opcja.opis()) {
                    opis.addAll(zawin(linia, w - 4));
                }
                opisy.add(opis);
                panel = Math.max(panel, opis.size());
            }
        }

        /** Szerokość każdej kolumny każdej tabeli; ukryte kolumny mają szerokość -1. */
        private Map<Integer, int[]> szerokosciKolumn(int dostepne) {
            Map<Integer, Integer> kolumn = new HashMap<>();
            for (Opcja opcja : opcje) {
                if (opcja.komorki() != null && opcja.tabela() >= 0) {
                    kolumn.merge(opcja.tabela(), opcja.komorki().size(), Math::max);
                }
            }
            Map<Integer, int[]> wynik = new HashMap<>();
            kolumn.forEach((tabela, ile) -> wynik.put(tabela, new int[ile]));
            for (Opcja opcja : opcje) {
                if (opcja.komorki() == null || opcja.tabela() < 0) {
                    continue;
                }
                int[] s = wynik.get(opcja.tabela());
                int ile = opcja.komorki().size();
                for (int k = 0; k < ile; k++) {
                    // Ostatnia komórka krótszego wiersza zajmuje resztę linii i nie poszerza kolumny.
                    if (k == ile - 1 && ile < s.length) {
                        continue;
                    }
                    s[k] = Math.max(s[k], new Linia(opcja.komorki().get(k)).szerokosc());
                }
            }
            for (Map.Entry<Integer, int[]> tabela : wynik.entrySet()) {
                int[] s = tabela.getValue();
                Set<Integer> ukryte = new HashSet<>();
                for (Integer kolumna : ukrywane.getOrDefault(tabela.getKey(), List.of())) {
                    if (calosc(s, ukryte) <= dostepne) {
                        break;
                    }
                    ukryte.add(kolumna);
                }
                for (int kolumna : ukryte) {
                    if (kolumna < s.length) {
                        s[kolumna] = -1;
                    }
                }
            }
            return wynik;
        }

        private int calosc(int[] s, Set<Integer> ukryte) {
            int suma = 0;
            int ile = 0;
            for (int k = 0; k < s.length; k++) {
                if (!ukryte.contains(k)) {
                    suma += s[k];
                    ile++;
                }
            }
            return suma + Math.max(0, ile - 1) * ODSTEP_KOLUMN;
        }

        /**
         * Wiersz tabeli: komórki wyrównane do kolumn. Ostatnia widoczna komórka dostaje resztę szerokości
         * i zawija się do kolejnych linii (wcięta pod swoją kolumną), więc nic nie wychodzi poza okno.
         */
        private List<Linia> wierszTabeli(Opcja opcja, int[] szer, int dostepne) {
            boolean przygaszony = opcja.wybieralna() && !opcja.aktywna();
            int ostatnia = -1;
            for (int k = 0; k < opcja.komorki().size(); k++) {
                if (szer[k] >= 0) {
                    ostatnia = k;
                }
            }
            List<Kawalek> poczatek = new ArrayList<>();
            int szerPoczatku = 0;
            for (int k = 0; k < ostatnia; k++) {
                if (szer[k] < 0) {
                    continue;
                }
                Linia komorka = new Linia(opcja.komorki().get(k));
                for (Kawalek kawalek : komorka.kawalki()) {
                    poczatek.add(przygas(kawalek, przygaszony));
                }
                int odstep = szer[k] - komorka.szerokosc() + ODSTEP_KOLUMN;
                poczatek.add(Kawalek.k(" ".repeat(odstep), Paleta.TEKST));
                szerPoczatku += komorka.szerokosc() + odstep;
            }
            List<Kawalek> koniec = new ArrayList<>();
            if (ostatnia >= 0) {
                for (Kawalek kawalek : opcja.komorki().get(ostatnia)) {
                    koniec.add(przygas(kawalek, przygaszony));
                }
            }
            List<Linia> wynik = new ArrayList<>();
            List<Linia> kawalkiKonca = zawin(new Linia(koniec), Math.max(8, dostepne - szerPoczatku));
            for (int i = 0; i < kawalkiKonca.size(); i++) {
                List<Kawalek> linia = new ArrayList<>(i == 0 ? poczatek
                        : List.of(Kawalek.k(" ".repeat(szerPoczatku), Paleta.TEKST)));
                linia.addAll(kawalkiKonca.get(i).kawalki());
                wynik.add(new Linia(linia));
            }
            return wynik;
        }

        /** Opcja niedostępna: cały wiersz przygaszony, powód jest w opisie. */
        private Kawalek przygas(Kawalek kawalek, boolean przygaszony) {
            return przygaszony ? new Kawalek(kawalek.tekst(), Paleta.TEKST_SLABY, false) : kawalek;
        }

        int wysokoscOpcji(int i) {
            return linie.get(i).size();
        }

        /** Wiersze poza opcjami: ramka, wstęp, odstępy, panel opisu i podpowiedź. */
        int stale() {
            return 2 + wiersze.size() + (wiersze.isEmpty() ? 0 : 1) + 2 + (panel > 0 ? panel + 1 : 0);
        }

        int calosc() {
            int suma = stale();
            for (int i = 0; i < opcje.size(); i++) {
                suma += wysokoscOpcji(i);
            }
            return suma;
        }
    }

    /** Pierwsza pozycja ciągu niewybieralnych wierszy tuż nad opcją (nagłówki zostają widoczne nad nią). */
    private int poczatekGrupy(int indeks) {
        int i = indeks;
        while (i > 0 && !opcje.get(i - 1).wybieralna()) {
            i--;
        }
        return i;
    }

    void rysuj(Ekran e, Strefy strefy) {
        // Najpierw docelowa szerokość; gdy treść nie mieści się w pionie, okno się poszerza.
        int maks = e.szer - MARGINES_EKRANU;
        Uklad uklad = new Uklad(Math.min(szerokoscDocelowa, maks));
        if (uklad.calosc() > e.wys && maks > uklad.w) {
            uklad = new Uklad(maks);
        }
        int w = uklad.w;
        int naOpcje = Math.max(1, e.wys - uklad.stale());

        // Okno przewijania: zaznaczona opcja (razem z nagłówkami nad nią) musi być w całości widoczna.
        pierwsza = Math.min(pierwsza, poczatekGrupy(wybrana));
        while (true) {
            int zajete = 0;
            int ile = 0;
            for (int i = pierwsza; i < opcje.size() && zajete + uklad.wysokoscOpcji(i) <= naOpcje; i++) {
                zajete += uklad.wysokoscOpcji(i);
                ile++;
            }
            widoczne = Math.max(1, ile);
            if (wybrana < pierwsza + widoczne || pierwsza >= wybrana) {
                break;
            }
            pierwsza++;
        }
        int wysOpcji = 0;
        for (int i = pierwsza; i < pierwsza + widoczne; i++) {
            wysOpcji += uklad.wysokoscOpcji(i);
        }
        int h = uklad.stale() + wysOpcji;
        int x = (e.szer - w) / 2;
        int y = Math.max(0, (e.wys - h) / 2);

        miejscaOpcji.clear();
        e.ramka(x, y, w, h, Paleta.ZLOTO, Paleta.PANEL, tytul, Paleta.ZLOTO);
        int wiersz = y + 1;
        for (Linia linia : uklad.wiersze) {
            pisz(e, x + 2, wiersz++, linia);
        }
        if (!uklad.wiersze.isEmpty()) {
            wiersz++;
        }
        int poczatekOpcji = wiersz;
        int numer = 0;
        for (int i = pierwsza; i < pierwsza + widoczne; i++) {
            Opcja opcja = opcje.get(i);
            boolean zaznaczona = i == wybrana && opcja.wybieralna();
            int wysokosc = uklad.wysokoscOpcji(i);
            if (opcja.wybieralna()) {
                e.wypelnij(x + 1, wiersz, w - 2, wysokosc, zaznaczona ? Paleta.PANEL_JASNY : Paleta.PANEL);
                e.pisz(x + 2, wiersz, zaznaczona ? "▶" : " ", Paleta.ZLOTO, Ekran.BEZ_ZMIAN, true);
                numer++;
                if (numer <= 9) {
                    e.pisz(x + 4, wiersz, String.valueOf(numer), opcja.aktywna() ? Paleta.ZLOTO : Paleta.TEKST_SLABY,
                            Ekran.BEZ_ZMIAN, true);
                }
                int indeks = i;
                strefy.dodaj(x + 1, wiersz, w - 2, wysokosc, () -> wybierz(indeks));
                miejscaOpcji.add(new int[] {x + 1, wiersz, w - 2, wysokosc, indeks});
            } else if (opcja.znacznik() != null) {
                e.pisz(x + 1, wiersz, opcja.znacznik(), Paleta.TEKST_SLABY);
            }
            // Linie bez kolumn (nagłówki sekcji) zaczynają się od lewego marginesu, reszta za numerem.
            int lewo = opcja.ramki() != null || !opcja.wybieralna() && opcja.tabela() < 0 && opcja.komorki() != null ? x + 2
                    : opcja.tabela() >= 0 && opcja.komorki() != null ? x + 7 : x + 6;
            int linia = wiersz;
            for (Linia tekst : uklad.linie.get(i)) {
                pisz(e, lewo, linia++, tekst);
            }
            wiersz += wysokosc;
        }
        if (pierwsza > 0) {
            e.pisz(x + w - 3, poczatekOpcji, "▲", Paleta.ZLOTO);
        }
        if (pierwsza + widoczne < opcje.size()) {
            e.pisz(x + w - 3, wiersz - 1, "▼", Paleta.ZLOTO);
        }
        if (uklad.panel > 0) {
            // Panel opisu zaznaczonej opcji, oddzielony linią.
            e.pisz(x, wiersz, "├" + "─".repeat(w - 2) + "┤", Paleta.RAMKA);
            int p = wiersz + 1;
            for (Linia tekst : uklad.opisy.get(wybrana)) {
                pisz(e, x + 2, p++, tekst);
            }
        }
        e.pisz(x + 2, y + h - 2, podpowiedz(w - 4), Paleta.TEKST_SLABY);
        if (anuluj != null) {
            String zamknij = " Esc ✕ ";
            int zx = x + w - Ekran.szerokosc(zamknij) - 2;
            e.pisz(zx, y, zamknij, Paleta.TEKST_CIEMNY, Paleta.ZLOTO, true);
            strefy.dodaj(zx, y, Ekran.szerokosc(zamknij), 1, anuluj);
        }
    }

    private static void pisz(Ekran e, int x, int y, Linia linia) {
        int px = x;
        for (Kawalek kawalek : linia.kawalki()) {
            px = e.pisz(px, y, kawalek.tekst(), kawalek.kolor(), Ekran.BEZ_ZMIAN, kawalek.gruby());
        }
    }

    /** Najdłuższa podpowiedź, która mieści się w oknie. */
    private String podpowiedz(int szerokosc) {
        boolean przewijanie = widoczne < opcje.size();
        String[] warianty = anuluj == null
                ? new String[] {"↑↓ wybór · Enter zatwierdza · można kliknąć", "↑↓ · Enter"}
                : przewijanie
                ? new String[] {"↑↓ wybór i przewijanie · Enter zatwierdza · Esc zamyka · można kliknąć",
                    "↑↓ wybór · Enter zatwierdza · Esc zamyka", "↑↓ · Enter · Esc"}
                : new String[] {"↑↓ wybór · Enter zatwierdza · Esc zamyka · można kliknąć",
                    "↑↓ wybór · Enter zatwierdza · Esc zamyka", "↑↓ · Enter · Esc"};
        for (String wariant : warianty) {
            if (Ekran.szerokosc(wariant) <= szerokosc) {
                return wariant;
            }
        }
        return "";
    }

    /** Najbliższa wybieralna opcja w danym kierunku (albo obecna, gdy dalej nie ma żadnej). */
    private int nastepna(int od, int krok) {
        for (int i = od + krok; i >= 0 && i < opcje.size(); i += krok) {
            if (opcje.get(i).wybieralna()) {
                return i;
            }
        }
        return wybrana;
    }

    /** Numer klawisza 1-9 odpowiada kolejnym wybieralnym opcjom widocznym w oknie. */
    private int opcjaONumerze(int numer) {
        int licznik = 0;
        for (int i = pierwsza; i < Math.min(opcje.size(), pierwsza + widoczne); i++) {
            if (opcje.get(i).wybieralna() && ++licznik == numer) {
                return i;
            }
        }
        return -1;
    }

    void obsluz(Wejscie wejscie) {
        switch (wejscie) {
            case Wejscie.Klawisz klawisz -> {
                switch (klawisz.rodzaj()) {
                    case GORA -> wybrana = nastepna(wybrana, -1);
                    case DOL -> wybrana = nastepna(wybrana, 1);
                    case STRONA_W_GORE -> {
                        for (int i = 0; i < widoczne; i++) {
                            wybrana = nastepna(wybrana, -1);
                        }
                    }
                    case STRONA_W_DOL -> {
                        for (int i = 0; i < widoczne; i++) {
                            wybrana = nastepna(wybrana, 1);
                        }
                    }
                    case ENTER -> wybierz(wybrana);
                    case ESC -> {
                        if (anuluj != null) {
                            anuluj.run();
                        }
                    }
                    default -> {
                    }
                }
            }
            case Wejscie.Znak znak -> {
                if (znak.znak() >= '1' && znak.znak() <= '9') {
                    int indeks = opcjaONumerze(znak.znak() - '0');
                    if (indeks >= 0) {
                        wybierz(indeks);
                    }
                } else if (znak.znak() == ' ') {
                    wybierz(wybrana);
                }
            }
            default -> {
            }
        }
    }

    private void wybierz(int indeks) {
        if (indeks < 0 || indeks >= opcje.size() || !opcje.get(indeks).wybieralna()) {
            return;
        }
        wybrana = indeks;
        Opcja opcja = opcje.get(indeks);
        if (opcja.aktywna()) {
            opcja.akcja().run();
        }
    }
}
