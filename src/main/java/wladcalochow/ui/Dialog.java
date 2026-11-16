package wladcalochow.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Okno wyboru na środku ekranu. Opcje wybiera się strzałkami i Enterem, cyfrą albo kliknięciem.
 * Tekst nigdy nie jest ucinany: długie akapity i opisy opcji zawijają się do kolejnych wierszy,
 * a gdy opcji jest więcej, niż mieści ekran, lista się przewija.
 */
final class Dialog {

    /** Opcja w oknie: etykieta, opis pod nią i akcja po wybraniu. */
    record Opcja(String etykieta, String szczegol, boolean aktywna, Runnable akcja) {
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

    private final String tytul;
    private final List<Wiersz> wstep;
    private final List<Opcja> opcje;
    private final Runnable anuluj;
    private int wybrana;
    private int pierwsza;
    private int widoczne = 1;

    Dialog(String tytul, List<String> wstep, List<Opcja> opcje, Runnable anuluj) {
        this(wstep.stream().map(tekst -> new Wiersz(tekst, Paleta.TEKST)).toList(), tytul, opcje, anuluj);
    }

    /** Wariant z kolorowymi akapitami (kolejność parametrów odróżnia go od wariantu z samym tekstem). */
    Dialog(List<Wiersz> wstep, String tytul, List<Opcja> opcje, Runnable anuluj) {
        this.tytul = tytul;
        this.wstep = wstep;
        this.opcje = opcje;
        this.anuluj = anuluj;
        for (int i = 0; i < opcje.size(); i++) {
            if (opcje.get(i).aktywna()) {
                wybrana = i;
                break;
            }
        }
    }

    /** Rozłożenie treści okna dla konkretnej szerokości. */
    private final class Uklad {
        final int w;
        final List<Wiersz> wiersze = new ArrayList<>();
        final List<List<String>> etykiety = new ArrayList<>();
        final List<List<String>> szczegoly = new ArrayList<>();

        Uklad(int w) {
            this.w = w;
            for (Wiersz akapit : wstep) {
                for (String linia : Ekran.zawin(akapit.tekst(), w - 4)) {
                    wiersze.add(new Wiersz(linia, akapit.kolor()));
                }
            }
            for (Opcja opcja : opcje) {
                etykiety.add(Ekran.zawin(opcja.etykieta(), w - 9));
                szczegoly.add(opcja.szczegol().isEmpty() ? List.of() : Ekran.zawin(opcja.szczegol(), w - 9));
            }
        }

        int wysokoscOpcji(int i) {
            return etykiety.get(i).size() + szczegoly.get(i).size();
        }

        /** Wiersze poza opcjami: ramka, wstęp, odstępy i podpowiedź. */
        int stale() {
            return 2 + wiersze.size() + (wiersze.isEmpty() ? 0 : 1) + 2;
        }

        int calosc() {
            int suma = stale();
            for (int i = 0; i < opcje.size(); i++) {
                suma += wysokoscOpcji(i);
            }
            return suma;
        }
    }

    void rysuj(Ekran e, Strefy strefy) {
        // Najpierw zwykła szerokość; gdy treść nie mieści się w pionie, okno się poszerza.
        Uklad uklad = new Uklad(Math.min(SZEROKOSC, e.szer - MARGINES_EKRANU));
        if (uklad.calosc() > e.wys && e.szer - MARGINES_EKRANU > uklad.w) {
            uklad = new Uklad(e.szer - MARGINES_EKRANU);
        }
        int w = uklad.w;
        int naOpcje = Math.max(1, e.wys - uklad.stale());

        // Okno przewijania: zaznaczona opcja musi być w całości widoczna.
        pierwsza = Math.min(pierwsza, wybrana);
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

        e.ramka(x, y, w, h, Paleta.ZLOTO, Paleta.PANEL, tytul, Paleta.ZLOTO);
        int wiersz = y + 1;
        for (Wiersz linia : uklad.wiersze) {
            e.pisz(x + 2, wiersz++, linia.tekst(), linia.kolor());
        }
        if (!uklad.wiersze.isEmpty()) {
            wiersz++;
        }
        int poczatekOpcji = wiersz;
        for (int i = pierwsza; i < pierwsza + widoczne; i++) {
            Opcja opcja = opcje.get(i);
            boolean zaznaczona = i == wybrana;
            int wysokosc = uklad.wysokoscOpcji(i);
            e.wypelnij(x + 1, wiersz, w - 2, wysokosc, zaznaczona ? Paleta.PANEL_JASNY : Paleta.PANEL);
            e.pisz(x + 2, wiersz, zaznaczona ? "▶" : " ", Paleta.ZLOTO, Ekran.BEZ_ZMIAN, true);
            int numer = i - pierwsza + 1;
            if (numer <= 9) {
                e.pisz(x + 4, wiersz, String.valueOf(numer), opcja.aktywna() ? Paleta.ZLOTO : Paleta.TEKST_SLABY,
                        Ekran.BEZ_ZMIAN, true);
            }
            int linia = wiersz;
            for (String tekst : uklad.etykiety.get(i)) {
                e.pisz(x + 6, linia++, tekst, opcja.aktywna() ? Paleta.TEKST : Paleta.TEKST_SLABY,
                        Ekran.BEZ_ZMIAN, opcja.aktywna());
            }
            for (String tekst : uklad.szczegoly.get(i)) {
                e.pisz(x + 6, linia++, tekst, Paleta.TEKST_SLABY);
            }
            int indeks = i;
            strefy.dodaj(x + 1, wiersz, w - 2, wysokosc, () -> wybierz(indeks));
            wiersz += wysokosc;
        }
        if (pierwsza > 0) {
            e.pisz(x + w - 3, poczatekOpcji, "▲", Paleta.ZLOTO);
        }
        if (pierwsza + widoczne < opcje.size()) {
            e.pisz(x + w - 3, wiersz - 1, "▼", Paleta.ZLOTO);
        }
        e.pisz(x + 2, y + h - 2, podpowiedz(w - 4), Paleta.TEKST_SLABY);
        if (anuluj != null) {
            String zamknij = " Esc ✕ ";
            int zx = x + w - Ekran.szerokosc(zamknij) - 2;
            e.pisz(zx, y, zamknij, Paleta.TEKST_CIEMNY, Paleta.ZLOTO, true);
            strefy.dodaj(zx, y, Ekran.szerokosc(zamknij), 1, anuluj);
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

    void obsluz(Wejscie wejscie) {
        switch (wejscie) {
            case Wejscie.Klawisz klawisz -> {
                switch (klawisz.rodzaj()) {
                    case GORA -> wybrana = Math.max(0, wybrana - 1);
                    case DOL -> wybrana = Math.min(opcje.size() - 1, wybrana + 1);
                    case STRONA_W_GORE -> wybrana = Math.max(0, wybrana - widoczne);
                    case STRONA_W_DOL -> wybrana = Math.min(opcje.size() - 1, wybrana + widoczne);
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
                    int indeks = pierwsza + (znak.znak() - '1');
                    if (indeks < Math.min(opcje.size(), pierwsza + widoczne)) {
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
        if (indeks < 0 || indeks >= opcje.size()) {
            return;
        }
        wybrana = indeks;
        Opcja opcja = opcje.get(indeks);
        if (opcja.aktywna()) {
            opcja.akcja().run();
        }
    }
}
