package wladcalochow.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Okno wyboru na środku ekranu. Opcje wybiera się strzałkami i Enterem, cyfrą albo kliknięciem.
 */
final class Dialog {

    /** Opcja w oknie: etykieta, drugi wiersz ze szczegółami i akcja po wybraniu. */
    record Opcja(String etykieta, String szczegol, boolean aktywna, Runnable akcja) {
    }

    /** Odbiorca stref klikalnych wyznaczanych podczas rysowania. */
    interface Strefy {
        void dodaj(int x, int y, int w, int h, Runnable akcja);
    }

    private static final int MAKS_SZER = 78;

    private final String tytul;
    private final List<String> wstep;
    private final List<Opcja> opcje;
    private final Runnable anuluj;
    private int wybrana;
    private int pierwsza;
    private int widoczne = 1;

    Dialog(String tytul, List<String> wstep, List<Opcja> opcje, Runnable anuluj) {
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

    void rysuj(Ekran e, Strefy strefy) {
        int w = Math.min(MAKS_SZER, e.szer - 6);
        int wnetrze = w - 4;
        List<String> wiersze = new ArrayList<>();
        for (String akapit : wstep) {
            wiersze.addAll(Ekran.zawin(akapit, wnetrze));
        }
        int stale = 2 + wiersze.size() + (wiersze.isEmpty() ? 0 : 1) + 2;
        widoczne = Math.max(1, Math.min(opcje.size(), (e.wys - 2 - stale) / 2));
        if (wybrana < pierwsza) {
            pierwsza = wybrana;
        } else if (wybrana >= pierwsza + widoczne) {
            pierwsza = wybrana - widoczne + 1;
        }
        int h = stale + 2 * widoczne;
        int x = (e.szer - w) / 2;
        int y = Math.max(0, (e.wys - h) / 2);

        e.ramka(x, y, w, h, Paleta.ZLOTO, Paleta.PANEL, tytul, Paleta.ZLOTO);
        int wiersz = y + 1;
        for (String linia : wiersze) {
            e.pisz(x + 2, wiersz++, linia, Paleta.TEKST);
        }
        if (!wiersze.isEmpty()) {
            wiersz++;
        }
        for (int i = pierwsza; i < pierwsza + widoczne; i++) {
            Opcja opcja = opcje.get(i);
            boolean zaznaczona = i == wybrana;
            int tlo = zaznaczona ? Paleta.PANEL_JASNY : Paleta.PANEL;
            e.wypelnij(x + 1, wiersz, w - 2, 2, tlo);
            int numer = i - pierwsza + 1;
            String znacznik = zaznaczona ? "▶" : " ";
            e.pisz(x + 2, wiersz, znacznik, Paleta.ZLOTO, Ekran.BEZ_ZMIAN, true);
            if (numer <= 9) {
                e.pisz(x + 4, wiersz, String.valueOf(numer), opcja.aktywna() ? Paleta.ZLOTO : Paleta.TEKST_SLABY,
                        Ekran.BEZ_ZMIAN, true);
            }
            e.pisz(x + 6, wiersz, Ekran.przytnij(opcja.etykieta(), w - 9),
                    opcja.aktywna() ? Paleta.TEKST : Paleta.TEKST_SLABY, Ekran.BEZ_ZMIAN, opcja.aktywna());
            e.pisz(x + 6, wiersz + 1, Ekran.przytnij(opcja.szczegol(), w - 9), Paleta.TEKST_SLABY);
            int indeks = i;
            strefy.dodaj(x + 1, wiersz, w - 2, 2, () -> wybierz(indeks));
            wiersz += 2;
        }
        if (pierwsza > 0) {
            e.pisz(x + w - 4, y + stale - 2, "▲", Paleta.TEKST_SLABY);
        }
        if (pierwsza + widoczne < opcje.size()) {
            e.pisz(x + w - 4, wiersz - 1, "▼", Paleta.TEKST_SLABY);
        }
        String podpowiedz = anuluj == null
                ? "↑↓ wybór · Enter zatwierdza · można kliknąć"
                : "↑↓ wybór · Enter zatwierdza · Esc zamyka · można kliknąć";
        e.pisz(x + 2, y + h - 2, Ekran.przytnij(podpowiedz, wnetrze), Paleta.TEKST_SLABY);
        if (anuluj != null) {
            String zamknij = " Esc ✕ ";
            int zx = x + w - Ekran.szerokosc(zamknij) - 2;
            e.pisz(zx, y, zamknij, Paleta.TEKST_CIEMNY, Paleta.ZLOTO, true);
            strefy.dodaj(zx, y, Ekran.szerokosc(zamknij), 1, anuluj);
        }
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
