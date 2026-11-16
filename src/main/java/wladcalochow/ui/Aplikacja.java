package wladcalochow.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import wladcalochow.model.BilansDnia;
import wladcalochow.model.Bohater;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Istota;
import wladcalochow.model.Loch;
import wladcalochow.model.MinisterFinansow;
import wladcalochow.model.MinisterPracy;
import wladcalochow.model.Potwor;
import wladcalochow.model.Walka;
import wladcalochow.model.Zdarzenie;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.RodzajRozkazu;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.model.strategia.Ulepszenie;
import wladcalochow.silnik.Gra;
import wladcalochow.silnik.ZapisGry;

/**
 * Interfejs gry: stały pulpit z panelami, przyciski pod klawiaturę i kliknięcia, okna wyboru.
 * Cała logika jest w modelu; ta klasa tylko pokazuje stan i przekazuje decyzje gracza.
 */
public final class Aplikacja {

    static final int MIN_SZER = 108;
    static final int MIN_WYS = 30;
    private static final int PANEL_SZER = 42;

    private enum Tryb { MENU, PORANEK, WALKA, WIECZOR, KONIEC_GRY }

    private record Strefa(int x, int y, int w, int h, Runnable akcja) {
        boolean zawiera(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private record Guzik(char klawisz, String etykieta, boolean glowny, boolean aktywny, Runnable akcja,
                         boolean nowy) {
        Guzik(char klawisz, String etykieta, boolean glowny, boolean aktywny, Runnable akcja) {
            this(klawisz, etykieta, glowny, aktywny, akcja, false);
        }
    }

    private static final String[] LITERY_TYTULU = {
        "█   █ █      ███  ████   ████  ███    █      ███   ████ █   █  ███  █   █",
        "█   █ █ █   █   █ █   █ █     █   █   █     █   █ █     █   █ █   █ █   █",
        "█ █ █ ██    █████ █   █ █     █████   █     █   █ █     █████ █   █ █ █ █",
        "██ ██ █     █   █ █   █ █     █   █   █     █   █ █     █   █ █   █ ██ ██",
        "█   █ █████ █   █ ████   ████ █   █   █████  ███   ████ █   █  ███  █   █",
    };
    private static final String AKCENT_TYTULU =
        "                                                                ▄▀         ";

    private final TerminalGry terminal;
    private final Path plikZapisu;
    private final Long ziarnoStartowe;
    private final List<Strefa> strefy = new ArrayList<>();
    private final Map<Character, Runnable> skroty = new HashMap<>();
    private Runnable akcjaEnter;

    private Gra gra;
    private Tryb tryb = Tryb.MENU;
    private Dialog dialog;
    private Ekran poprzedni;
    private boolean koniec;
    private boolean auto;
    private Istota aktor;
    private Istota cel;
    private int przewiniecie;
    private String komunikat;
    private boolean radaWalkiPokazana;

    public Aplikacja(Path plikZapisu, Long ziarnoStartowe) throws IOException {
        this.terminal = new TerminalGry();
        this.plikZapisu = plikZapisu;
        this.ziarnoStartowe = ziarnoStartowe;
    }

    public void uruchom() throws IOException {
        try {
            while (!koniec) {
                rysuj();
                boolean samaGra = tryb == Tryb.WALKA && auto && dialog == null;
                Wejscie wejscie = terminal.czytaj(samaGra ? 450 : 300);
                switch (wejscie) {
                    case Wejscie.Brak brak -> {
                        if (samaGra) {
                            krokWalki();
                        }
                    }
                    case Wejscie.Koniec k -> {
                        zapisz();
                        koniec = true;
                    }
                    default -> obsluz(wejscie);
                }
            }
        } finally {
            terminal.close();
        }
    }

    // ================= wejście =================

    private void obsluz(Wejscie wejscie) {
        if (wejscie instanceof Wejscie.Klik klik) {
            for (int i = strefy.size() - 1; i >= 0; i--) {
                if (strefy.get(i).zawiera(klik.x(), klik.y())) {
                    strefy.get(i).akcja().run();
                    return;
                }
            }
            return;
        }
        if (dialog != null) {
            dialog.obsluz(wejscie);
            return;
        }
        switch (wejscie) {
            case Wejscie.Znak znak -> {
                Runnable akcja = skroty.get(Character.toLowerCase(znak.znak()));
                if (akcja != null) {
                    akcja.run();
                } else if (znak.znak() == ' ' && akcjaEnter != null) {
                    akcjaEnter.run();
                }
            }
            case Wejscie.Klawisz klawisz -> {
                switch (klawisz.rodzaj()) {
                    case ENTER -> {
                        if (akcjaEnter != null) {
                            akcjaEnter.run();
                        }
                    }
                    case GORA -> przewiniecie++;
                    case DOL -> przewiniecie = Math.max(0, przewiniecie - 1);
                    case STRONA_W_GORE -> przewiniecie += 5;
                    case STRONA_W_DOL -> przewiniecie = Math.max(0, przewiniecie - 5);
                    default -> {
                    }
                }
            }
            default -> {
            }
        }
    }

    // ================= rysowanie =================

    private void rysuj() {
        int szer = terminal.szer();
        int wys = terminal.wys();
        Ekran e = new Ekran(Math.max(1, szer), Math.max(1, wys), Paleta.TLO);
        strefy.clear();
        skroty.clear();
        akcjaEnter = null;
        if (szer < MIN_SZER || wys < MIN_WYS) {
            String prosba = "Powiększ okno terminala: potrzeba " + MIN_SZER + "×" + MIN_WYS
                    + ", jest " + szer + "×" + wys + ".";
            int y = wys / 2;
            for (String wiersz : Ekran.zawin(prosba, Math.max(10, szer - 4))) {
                e.pisz(Math.max(0, (szer - Ekran.szerokosc(wiersz)) / 2), y++, wiersz, Paleta.ZLOTO);
            }
            skroty.put('q', this::wyjdzZGry);
        } else {
            if (tryb == Tryb.MENU) {
                rysujMenu(e);
            } else {
                rysujPulpit(e);
            }
            if (dialog != null) {
                strefy.clear();
                skroty.clear();
                akcjaEnter = null;
                dialog.rysuj(e, (x, y, w, h, akcja) -> strefy.add(new Strefa(x, y, w, h, akcja)));
            }
        }
        terminal.wypisz(e.ansi(poprzedni, terminal.pelnyKolor()));
        poprzedni = e;
    }

    // ---- menu główne ----

    private void rysujMenu(Ekran e) {
        int szerTytulu = LITERY_TYTULU[0].length();
        int tx = (e.szer - szerTytulu) / 2;
        int wolne = e.wys - MIN_WYS;
        int ty = 2 + wolne / 3;
        e.pisz(tx, ty - 1, AKCENT_TYTULU, Paleta.ZLOTO);
        for (int i = 0; i < LITERY_TYTULU.length; i++) {
            int kolor = Paleta.mieszaj(0xF7D36B, Paleta.POMARANCZ, i / (double) (LITERY_TYTULU.length - 1));
            e.pisz(tx, ty + i, LITERY_TYTULU[i], kolor);
        }
        String podtytul = "Zatrudniaj potwory, kop złoto i broń skarbca przed bohaterami.";
        e.pisz((e.szer - Ekran.szerokosc(podtytul)) / 2, ty + 6, podtytul, Paleta.TEKST_SLABY);

        int sw = Math.min(e.szer - 4, 120);
        Scena.rysuj(e, (e.szer - sw) / 2, ty + 8, sw, Scena.Pora.NOC, List.of(), List.of(), null, null);

        boolean jestZapis = ZapisGry.istnieje(plikZapisu);
        List<Guzik> guziki = new ArrayList<>();
        guziki.add(new Guzik('k', "Kontynuuj", false, jestZapis, this::kontynuuj));
        guziki.add(new Guzik('h', "Pomoc", false, true, this::dialogPomoc));
        guziki.add(new Guzik('q', "Wyjście", false, true, this::wyjdzZGry));
        guziki.add(new Guzik('n', "Nowa gra", true, true, this::nowaGra));
        if (komunikat != null) {
            e.pisz(2, e.wys - 4, komunikat, Paleta.CZERWIEN);
        }
        pasek(e, guziki);
    }

    // ---- pulpit ----

    private void rysujPulpit(Ekran e) {
        int sw = e.szer - PANEL_SZER;
        int dol = e.wys - 3;
        naglowek(e);

        Walka walka = gra.getDzien().getWalka();
        boolean pokazWalke = tryb == Tryb.WALKA && walka != null;
        List<? extends Istota> lewa = pokazWalke ? walka.getBohaterowie() : List.of();
        List<? extends Istota> prawa = pokazWalke ? obroncyWalki(walka) : gra.getLoch().getListaPotworow();
        Scena.Pora pora = switch (tryb) {
            case PORANEK -> Scena.Pora.PORANEK;
            case WALKA -> Scena.Pora.DZIEN;
            default -> Scena.Pora.WIECZOR;
        };
        Scena.rysuj(e, 0, 1, sw, pora, lewa, prawa, aktor, cel);
        osCzasu(e);

        panelBoczny(e, sw, 1, PANEL_SZER, dol - 1, pokazWalke ? walka : null);
        dziennik(e, 0, 1 + Scena.WYS, sw, dol - 1 - Scena.WYS);
        pasek(e, guzikiPulpitu(walka));
    }

    private static List<Potwor> obroncyWalki(Walka walka) {
        List<Potwor> wszyscy = new ArrayList<>(walka.getObroncy());
        wszyscy.addAll(walka.getOdwod());
        return wszyscy;
    }

    private void naglowek(Ekran e) {
        Loch loch = gra.getLoch();
        DzienGry dzien = gra.getDzien();
        e.wypelnij(0, 0, e.szer, 1, Paleta.NAGLOWEK);
        int x = 1;
        x = e.pisz(x, 0, Sprite.KORONA + " WŁADCA LOCHÓW", Paleta.ZLOTO, Ekran.BEZ_ZMIAN, true);
        x = e.pisz(x, 0, "  │  ", Paleta.RAMKA);
        x = e.pisz(x, 0, "Dzień " + gra.getNumerDnia(), Paleta.TEKST, Ekran.BEZ_ZMIAN, true);
        x = e.pisz(x, 0, "  │  ", Paleta.RAMKA);
        x = e.pisz(x, 0, Sprite.ZLOTO + " " + loch.getSkarbiec().getZloto(), Paleta.ZLOTO, Ekran.BEZ_ZMIAN, true);
        x = e.pisz(x, 0, "  │  ", Paleta.RAMKA);
        x = e.pisz(x, 0, "Rozkazy ", Paleta.TEKST_SLABY);
        int rozkazy = tryb == Tryb.PORANEK ? dzien.getPozostaleRozkazy() : 0;
        for (int i = 0; i < dzien.getLimitRozkazow(); i++) {
            x = e.pisz(x, 0, i < rozkazy ? "◆" : "◇", i < rozkazy ? Paleta.TURKUS : Paleta.TEKST_SLABY);
        }
        if (dzien.getLimitRozkazow() == 0) {
            x = e.pisz(x, 0, "brak", Paleta.TEKST_SLABY);
        }
        x = e.pisz(x, 0, "  │  ", Paleta.RAMKA);
        x = e.pisz(x, 0, "Postawa: ", Paleta.TEKST_SLABY);
        x = e.pisz(x, 0, loch.getPostawa().nazwa(), Paleta.TEKST);
        x = e.pisz(x, 0, "  │  ", Paleta.RAMKA);
        x = e.pisz(x, 0, "Porażki ", Paleta.TEKST_SLABY);
        for (int i = 0; i < Loch.PORAZKI_DO_UPADKU; i++) {
            boolean zajeta = i < loch.getPorazkiZRzedu();
            x = e.pisz(x, 0, zajeta ? "■" : "□", zajeta ? Paleta.CZERWIEN : Paleta.TEKST_SLABY);
        }
    }

    /** Pasek z czterema fazami dnia; bieżąca faza jest podświetlona. */
    private void osCzasu(Ekran e) {
        String[] fazy = {"1 Rozkazy", "2 Rekrutacja", "3 Obrona", "4 Żołd"};
        int biezaca = switch (tryb) {
            case PORANEK -> 0;
            case WALKA -> 2;
            default -> 3;
        };
        int x = 1;
        for (int i = 0; i < fazy.length; i++) {
            if (i == biezaca) {
                x = e.pisz(x, 1, " " + fazy[i] + " ", Paleta.TEKST_CIEMNY, Paleta.ZLOTO, true);
            } else if (i < biezaca) {
                x = e.pisz(x, 1, " " + fazy[i] + " ", Paleta.TEKST, 0x2F6B4F, false);
            } else {
                x = e.pisz(x, 1, " " + fazy[i] + " ", Paleta.TEKST_SLABY, Paleta.PANEL_JASNY, false);
            }
            if (i < fazy.length - 1) {
                x = e.pisz(x, 1, "›", Paleta.TEKST_SLABY, Paleta.PANEL, false);
            }
        }
    }

    /** Krótka podpowiedź, co gracz może teraz zrobić. */
    private String coTeraz() {
        Walka walka = gra.getDzien().getWalka();
        return switch (tryb) {
            case PORANEK -> {
                int rozkazy = gra.getDzien().getPozostaleRozkazy();
                yield rozkazy > 0
                        ? "Wydaj rozkaz (zostało " + rozkazy + ") albo Enter: start dnia"
                        : "Enter rozpoczyna dzień";
            }
            case WALKA -> {
                if (walka == null || !walka.czyZakonczona()) {
                    int runda = walka == null ? 1 : Math.max(1, walka.getNumerRundy());
                    yield "Runda " + runda + " · Enter: ruch · A: auto · F: do końca";
                }
                yield (walka.getWynik() == Walka.Wynik.OBRONA_UDANA ? "Loch obroniony" : "Obrona przełamana")
                        + " · Enter: zakończ dzień";
            }
            case WIECZOR -> "Dzień zakończony · Enter: następny dzień";
            default -> "";
        };
    }

    // ---- panel boczny ----

    private void panelBoczny(Ekran e, int x, int y, int w, int h, Walka walka) {
        Loch loch = gra.getLoch();
        if (walka != null) {
            List<Bohater> bohaterowie = walka.getBohaterowie();
            int wysBohaterow = Math.min(bohaterowie.size(), Math.max(3, h / 2 - 2)) + 2;
            e.ramka(x, y, w, wysBohaterow, Paleta.RAMKA, Paleta.PANEL,
                    "Bohaterowie", Paleta.CZERWIEN);
            listaIstot(e, x + 1, y + 1, w - 2, wysBohaterow - 2, bohaterowie, walka);
            List<Potwor> obroncy = obroncyWalki(walka);
            e.ramka(x, y + wysBohaterow, w, h - wysBohaterow, Paleta.RAMKA, Paleta.PANEL,
                    "Obrońcy · w kopalni: " + loch.gornicy().size(), Paleta.ZIELEN);
            listaIstot(e, x + 1, y + wysBohaterow + 1, w - 2, h - wysBohaterow - 2, obroncy, walka);
            legenda(e, x, y + wysBohaterow - 1, w, "liczba: siła ataku");
            legenda(e, x, y + h - 1, w, "★ poziom  ♥ życie  ☻ morale");
            return;
        }

        MinisterFinansow finansow = loch.getMinisterFinansow();
        MinisterPracy pracy = loch.getMinisterPracy();
        e.ramka(x, y, w, 8, Paleta.RAMKA, Paleta.PANEL, "Ministrowie", Paleta.ZLOTO);
        int tx = x + 2;
        int tw = w - 4;
        e.pisz(tx, y + 1, Sprite.FINANSE + " " + Ekran.przytnij(finansow.getImie(), tw - 3), Paleta.TEKST,
                Ekran.BEZ_ZMIAN, true);
        e.pisz(tx + 3, y + 2, Ekran.przytnij("Finanse · " + finansow.nazwaRodzaju() + " · pensja "
                + finansow.getPensja(), tw - 3), Paleta.TEKST_SLABY);
        String nastepcaFinansow = gra.getNastepnyMinisterFinansow() == null ? ""
                : " · jutro: " + gra.getNastepnyMinisterFinansow().getImie();
        e.pisz(tx + 3, y + 3, Ekran.przytnij("negocjacje -" + procent(finansow.getNegocjacje()) + "%"
                + nastepcaFinansow, tw - 3), Paleta.TEKST_SLABY);
        e.pisz(tx, y + 4, Sprite.KADRY + " " + Ekran.przytnij(pracy.getImie(), tw - 3), Paleta.TEKST,
                Ekran.BEZ_ZMIAN, true);
        e.pisz(tx + 3, y + 5, Ekran.przytnij("Kadry · " + pracy.nazwaRodzaju() + " · pensja " + pracy.getPensja(),
                tw - 3), Paleta.TEKST_SLABY);
        String nastepcaPracy = gra.getNastepnyMinisterPracy() == null ? ""
                : " · jutro: " + gra.getNastepnyMinisterPracy().getImie();
        e.pisz(tx + 3, y + 6, Ekran.przytnij("werbunek +" + pracy.getWerbunek() + " · kopalnia +"
                + procent(pracy.getNadzorKopalni()) + "%" + nastepcaPracy, tw - 3), Paleta.TEKST_SLABY);

        e.ramka(x, y + 8, w, 4, Paleta.RAMKA, Paleta.PANEL, "Loch", Paleta.TURKUS);
        int lx = e.pisz(tx, y + 9, "Rekrutacja do " + loch.getLimitRekrutacji() + "%", Paleta.TEKST);
        if (loch.getWydobycieDoOdebrania() > 0) {
            e.pisz(lx, y + 9, " · kopalnia jutro +" + loch.getWydobycieDoOdebrania(), Paleta.TURKUS);
        }
        int ux = tx;
        for (Ulepszenie ulepszenie : Ulepszenie.values()) {
            ux = e.pisz(ux, y + 10, skrotUlepszenia(ulepszenie) + " ", Paleta.TEKST_SLABY);
            for (int i = 0; i < Ulepszenie.MAX_POZIOM; i++) {
                boolean jest = i < loch.poziom(ulepszenie);
                ux = e.pisz(ux, y + 10, jest ? "■" : "□", jest ? Paleta.TURKUS : Paleta.TEKST_SLABY);
            }
            ux += 2;
        }

        List<Potwor> potwory = loch.getListaPotworow();
        e.ramka(x, y + 12, w, h - 12, Paleta.RAMKA, Paleta.PANEL,
                "Potwory " + potwory.size() + "/" + Loch.POJEMNOSC, Paleta.ZIELEN);
        listaIstot(e, x + 1, y + 13, w - 2, h - 14, potwory, null);
        legenda(e, x, y + h - 1, w, "★ poziom  ♥ życie  ☻ morale");
    }

    /** Objaśnienie symboli wpisane w dolną krawędź ramki. */
    private static void legenda(Ekran e, int x, int y, int w, String tekst) {
        String opis = " " + tekst + " ";
        e.pisz(x + w - Ekran.szerokosc(opis) - 2, y, opis, Paleta.TEKST_SLABY);
    }

    private static String skrotUlepszenia(Ulepszenie ulepszenie) {
        return switch (ulepszenie) {
            case KOPALNIA -> "Kopalnia";
            case PULAPKI -> "Pułapki";
            case ZAMEK_SKARBCA -> "Zamek";
        };
    }

    private static int procent(double ulamek) {
        return (int) Math.round(ulamek * 100);
    }

    private void listaIstot(Ekran e, int x, int y, int w, int h, List<? extends Istota> istoty, Walka walka) {
        if (istoty.isEmpty()) {
            e.pisz(x + 1, y, "(nikogo)", Paleta.TEKST_SLABY);
            return;
        }
        int pokazane = istoty.size() <= h ? istoty.size() : h - 1;
        for (int i = 0; i < pokazane; i++) {
            wierszIstoty(e, x + 1, y + i, w - 1, istoty.get(i), walka);
        }
        if (pokazane < istoty.size()) {
            e.pisz(x + 1, y + pokazane, "… i jeszcze " + (istoty.size() - pokazane), Paleta.TEKST_SLABY);
        }
    }

    private void wierszIstoty(Ekran e, int x, int y, int w, Istota istota, Walka walka) {
        boolean aktywna = istota.wWalce();
        if (istota == aktor || istota == cel) {
            e.wypelnij(x - 1, y, w + 1, 1, Paleta.PANEL_JASNY);
        }
        int kolorImienia = !aktywna ? Paleta.TEKST_SLABY : istota == aktor ? Paleta.ZLOTO
                : istota == cel ? Paleta.CZERWIEN : Paleta.TEKST;
        int px = e.pisz(x, y, Sprite.wStanie(istota), Paleta.TEKST) + 1;
        int szerImienia = w - 26;
        px = e.pisz(px, y, Ekran.dopelnij(istota.getImie(), szerImienia), kolorImienia, Ekran.BEZ_ZMIAN,
                istota == aktor) + 1;
        if (istota instanceof Potwor potwor) {
            px = e.pisz(px, y, "★" + potwor.getPoziom(), Paleta.ZLOTO) + 1;
        } else {
            px = e.pisz(px, y, String.format("%2d", Math.min(99, istota.getSilaAtaku())), Paleta.POMARANCZ) + 1;
        }
        px = pasek(e, px, y, "♥", istota.procentZycia(), Paleta.zycie(istota.procentZycia()), Paleta.CZERWIEN) + 1;
        px = pasek(e, px, y, "☻", istota.procentMorale(), Paleta.morale(istota.procentMorale()),
                Paleta.NIEBIESKI) + 1;

        String znacznik = "";
        int kolorZnacznika = Paleta.TEKST_SLABY;
        if (!istota.wWalce()) {
            znacznik = "";
        } else if (istota.czyOgluszona()) {
            znacznik = "ogł.";
            kolorZnacznika = Paleta.FIOLET;
        } else if (istota instanceof Potwor potwor && potwor.czyWKopalni()) {
            znacznik = "kop.";
            kolorZnacznika = Paleta.TURKUS;
        } else if (walka != null && walka.getOdwod().contains(istota)) {
            znacznik = "odwód";
            kolorZnacznika = Paleta.NIEBIESKI;
        }
        e.pisz(px, y, znacznik, kolorZnacznika);
    }

    /** Pasek z pięciu kratek poprzedzony symbolem. Zwraca pozycję x za paskiem. */
    private static int pasek(Ekran e, int x, int y, String symbol, double procent, int kolor, int kolorSymbolu) {
        x = e.pisz(x, y, symbol, kolorSymbolu);
        int pelne = procent <= 0 ? 0 : Math.max(1, (int) Math.round(procent * 5));
        x = e.pisz(x, y, "━".repeat(pelne), kolor);
        return e.pisz(x, y, "━".repeat(5 - pelne), Paleta.RAMKA);
    }

    // ---- dziennik ----

    private void dziennik(Ekran e, int x, int y, int w, int h) {
        e.ramka(x, y, w, h, Paleta.RAMKA, Paleta.PANEL, "Dziennik", Paleta.POMARANCZ);
        String podpowiedz = " " + Ekran.przytnij(coTeraz(), w - 17) + " ";
        e.pisz(x + w - Ekran.szerokosc(podpowiedz) - 2, y, podpowiedz, Paleta.ZLOTO, Ekran.BEZ_ZMIAN, true);
        int wiersze = h - 2;
        int szerTekstu = w - 4;
        List<Gra.WpisDziennika> wpisy = gra.getDziennik();
        List<String> teksty = new ArrayList<>();
        List<Integer> kolory = new ArrayList<>();
        List<Boolean> naglowki = new ArrayList<>();
        for (int i = Math.max(0, wpisy.size() - 80); i < wpisy.size(); i++) {
            Gra.WpisDziennika wpis = wpisy.get(i);
            boolean naglowek = wpis.ton() == Zdarzenie.Ton.NAGLOWEK;
            String tekst = naglowek ? "── " + wpis.tekst() + " ──" : wpis.tekst();
            boolean pierwszy = true;
            for (String wiersz : Ekran.zawin(tekst, szerTekstu - 2)) {
                teksty.add((naglowek ? "" : pierwszy ? "· " : "  ") + wiersz);
                kolory.add(Paleta.ton(wpis.ton()));
                naglowki.add(naglowek);
                pierwszy = false;
            }
        }
        int maksPrzewiniecie = Math.max(0, teksty.size() - wiersze);
        przewiniecie = Math.max(0, Math.min(przewiniecie, maksPrzewiniecie));
        int koniecListy = teksty.size() - przewiniecie;
        int start = Math.max(0, koniecListy - wiersze);
        for (int i = start; i < koniecListy; i++) {
            e.pisz(x + 2, y + 1 + i - start, teksty.get(i), kolory.get(i), Ekran.BEZ_ZMIAN, naglowki.get(i));
        }
        if (przewiniecie > 0) {
            String info = " ↓ nowsze wpisy poniżej ";
            e.pisz(x + w - Ekran.szerokosc(info) - 2, y + h - 1, info, Paleta.TEKST_SLABY);
        } else if (maksPrzewiniecie > 0) {
            String info = " ↑↓ przewijanie ";
            e.pisz(x + w - Ekran.szerokosc(info) - 2, y + h - 1, info, Paleta.TEKST_SLABY);
        }
    }

    // ---- przyciski ----

    private List<Guzik> guzikiPulpitu(Walka walka) {
        List<Guzik> guziki = new ArrayList<>();
        switch (tryb) {
            case PORANEK -> {
                guzikRozkazu(guziki, RodzajRozkazu.MINISTROWIE, 'm', this::dialogMinistrowie);
                guzikRozkazu(guziki, RodzajRozkazu.BUDZET, 'b', this::dialogBudzet);
                guzikRozkazu(guziki, RodzajRozkazu.POSTAWA, 'p', this::dialogPostawa);
                guzikRozkazu(guziki, RodzajRozkazu.POTWORY, 'o', this::dialogPotwory);
                guzikRozkazu(guziki, RodzajRozkazu.ULEPSZENIA, 'u', this::dialogUlepszenia);
                guziki.add(new Guzik('h', "Pomoc", false, true, this::dialogPomoc));
                guziki.add(new Guzik('q', "Wyjdź", false, true, this::wyjdzDoMenu));
                guziki.add(new Guzik('\n', "Rozpocznij dzień", true, true, this::rozegrajDzien));
            }
            case WALKA -> {
                boolean trwa = walka != null && !walka.czyZakonczona();
                guziki.add(new Guzik('a', auto ? "Auto: wł." : "Auto: wył.", false, trwa, () -> auto = !auto));
                guziki.add(new Guzik('f', "Przewiń do końca", false, trwa, this::przewinWalke));
                guziki.add(new Guzik('h', "Pomoc", false, true, this::dialogPomoc));
                if (trwa) {
                    guziki.add(new Guzik('\n', "Następny ruch", true, true, this::krokWalki));
                } else {
                    guziki.add(new Guzik('\n', "Zakończ dzień", true, true, this::zakonczDzien));
                }
            }
            case WIECZOR -> {
                guziki.add(new Guzik('s', "Podsumowanie", false, true, this::dialogPodsumowanie));
                guziki.add(new Guzik('h', "Pomoc", false, true, this::dialogPomoc));
                guziki.add(new Guzik('q', "Wyjdź", false, true, this::wyjdzDoMenu));
                guziki.add(new Guzik('\n', "Następny dzień", true, true, this::nastepnyDzien));
            }
            default -> {
            }
        }
        return guziki;
    }

    /** Przycisk grupy rozkazów pojawia się dopiero, gdy grupa jest odblokowana. */
    private void guzikRozkazu(List<Guzik> guziki, RodzajRozkazu rodzaj, char klawisz, Runnable akcja) {
        if (!gra.czyOdblokowany(rodzaj)) {
            return;
        }
        boolean aktywny = gra.getDzien().maRozkazy() || rodzaj == RodzajRozkazu.POTWORY;
        guziki.add(new Guzik(klawisz, rodzaj.nazwa(), false, aktywny, akcja, gra.czyNowy(rodzaj)));
    }

    /** Rysuje rząd przycisków na dole ekranu i rejestruje ich skróty oraz strefy kliknięć. */
    private void pasek(Ekran e, List<Guzik> guziki) {
        int y = e.wys - 3;
        int x = 1;
        for (Guzik guzik : guziki) {
            String klawisz = guzik.klawisz() == '\n' ? "⏎" : String.valueOf(Character.toUpperCase(guzik.klawisz()));
            int w = Ekran.szerokosc(klawisz) + 1 + Ekran.szerokosc(guzik.etykieta()) + 2;
            int gx = guzik.glowny() ? e.szer - w - 1 : x;
            int tlo = !guzik.aktywny() ? Paleta.GUZIK_WYLACZONY
                    : guzik.glowny() ? Paleta.GUZIK_GLOWNY : guzik.nowy() ? Paleta.GUZIK_NOWY : Paleta.GUZIK;
            e.pisz(gx, y, "▄".repeat(w), tlo);
            e.wypelnij(gx, y + 1, w, 1, tlo);
            e.pisz(gx, y + 2, "▀".repeat(w), tlo);
            int kolorKlawisza = !guzik.aktywny() ? Paleta.TEKST_SLABY
                    : guzik.glowny() ? 0xFFF3C4 : Paleta.ZLOTO;
            int kolorTekstu = guzik.aktywny() ? Paleta.TEKST : Paleta.TEKST_SLABY;
            int tx = e.pisz(gx + 1, y + 1, klawisz, kolorKlawisza, Ekran.BEZ_ZMIAN, true);
            e.pisz(tx + 1, y + 1, guzik.etykieta(), kolorTekstu, Ekran.BEZ_ZMIAN, guzik.glowny());
            if (guzik.aktywny()) {
                strefy.add(new Strefa(gx, y, w, 3, guzik.akcja()));
                if (guzik.glowny()) {
                    akcjaEnter = guzik.akcja();
                }
                if (guzik.klawisz() != '\n') {
                    skroty.put(guzik.klawisz(), guzik.akcja());
                }
            }
            if (!guzik.glowny()) {
                x += w + 1;
            }
        }
    }

    // ================= przebieg gry =================

    private void nowaGra() {
        komunikat = null;
        dialog = new Dialog("Nowa gra",
                List.of("Samouczek prowadzi przez pierwsze 5 dni: możliwości pojawiają się po jednej, "
                        + "a doradca tłumaczy, co się dzieje."),
                List.of(new Dialog.Opcja("Z samouczkiem", "Zalecane za pierwszym razem.", true,
                                () -> zacznijNowaGre(true)),
                        new Dialog.Opcja("Bez samouczka", "Wszystkie rozkazy dostępne od pierwszego dnia.", true,
                                () -> zacznijNowaGre(false))),
                this::zamknijDialog);
    }

    private void zacznijNowaGre(boolean samouczek) {
        long ziarno = ziarnoStartowe != null ? ziarnoStartowe : System.nanoTime();
        gra = samouczek ? Gra.nowaZSamouczkiem(ziarno) : Gra.nowa(ziarno);
        if (samouczek) {
            strona(0, "Wybierz ministrów", this::wybierzMinistraFinansow, this::porzucNowaGre);
        } else {
            wybierzMinistraFinansow();
        }
    }

    /** Strona wprowadzenia lub pomocy z przyciskami Dalej i Wstecz. */
    private void strona(int numer, String etykietaKonca, Runnable poZakonczeniu, Runnable anuluj) {
        Doradca.Strona strona = Doradca.STRONY.get(numer);
        boolean ostatnia = numer == Doradca.STRONY.size() - 1;
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(ostatnia
                ? new Dialog.Opcja(etykietaKonca, "To już ostatnia strona.", true, poZakonczeniu)
                : new Dialog.Opcja("Dalej", "Następna strona: " + Doradca.STRONY.get(numer + 1).tytul() + ".", true,
                        () -> strona(numer + 1, etykietaKonca, poZakonczeniu, anuluj)));
        if (numer > 0) {
            opcje.add(new Dialog.Opcja("Wstecz", "Poprzednia strona.", true,
                    () -> strona(numer - 1, etykietaKonca, poZakonczeniu, anuluj)));
        }
        List<Dialog.Wiersz> wiersze = new ArrayList<>();
        for (String akapit : strona.akapity()) {
            if (!wiersze.isEmpty()) {
                wiersze.add(new Dialog.Wiersz("", Paleta.TEKST));
            }
            wiersze.add(new Dialog.Wiersz(akapit, Paleta.TEKST));
        }
        dialog = new Dialog(wiersze, strona.tytul() + " · " + (numer + 1) + "/" + Doradca.STRONY.size(),
                opcje, anuluj);
    }

    /** W samouczku do wyboru jest dwóch kandydatów o przeciwnych stylach, poza nim trzech. */
    private <T> List<T> kandydaciStartowi(List<T> wszyscy) {
        return gra.czyTrwaSamouczek() && wszyscy.size() > 2 ? wszyscy.subList(0, 2) : wszyscy;
    }

    private void wybierzMinistraFinansow() {
        List<Dialog.Opcja> opcje = new ArrayList<>();
        for (MinisterFinansow kandydat : kandydaciStartowi(gra.getStartowiFinansow())) {
            opcje.add(new Dialog.Opcja(kandydat.getImie() + " · " + kandydat.nazwaRodzaju() + " · pensja "
                    + kandydat.getPensja() + " · zniżki " + procent(kandydat.getNegocjacje()) + "%",
                    kandydat.opisStylu(), true, () -> wybierzMinistraPracy(kandydat)));
        }
        dialog = new Dialog("Wybierz Ministra Finansów",
                List.of("Minister Finansów pilnuje złota: decyduje, ile wydać na nowe potwory, i wypłaca żołd. "
                                + "Zniżki obniżają ceny ulepszeń, łapówek i zmian ministrów.",
                        "Nie ma złego wyboru. Ministra można później wymienić."),
                opcje, this::porzucNowaGre);
    }

    private void wybierzMinistraPracy(MinisterFinansow finansow) {
        List<Dialog.Opcja> opcje = new ArrayList<>();
        for (MinisterPracy kandydat : kandydaciStartowi(gra.getStartowiPracy())) {
            opcje.add(new Dialog.Opcja(kandydat.getImie() + " · " + kandydat.nazwaRodzaju() + " · pensja "
                    + kandydat.getPensja() + " · werbunek +" + kandydat.getWerbunek() + " · kopalnia +"
                    + procent(kandydat.getNadzorKopalni()) + "%",
                    kandydat.opisStylu(), true, () -> {
                        gra.rozpocznij(finansow, kandydat);
                        dialog = null;
                        tryb = Tryb.PORANEK;
                        przewiniecie = 0;
                        zapisz();
                        radaPoranna();
                    }));
        }
        dialog = new Dialog("Wybierz Ministra Pracy",
                List.of("Minister Pracy zatrudnia potwory i wysyła co trzeciego do kopalni. Werbunek to dodatkowi "
                                + "kandydaci co dzień, a premia do kopalni zwiększa wydobycie złota.",
                        "Tego ministra też można później wymienić."),
                opcje, this::porzucNowaGre);
    }

    // ---- doradca ----

    private void radaPoranna() {
        if (!gra.czyTrwaSamouczek()) {
            return;
        }
        pokazRade("Doradca · dzień " + gra.getNumerDnia(), Doradca.poranek(gra.getNumerDnia()));
    }

    private void pokazRade(String tytul, List<String> akapity) {
        if (akapity.isEmpty()) {
            return;
        }
        List<Dialog.Wiersz> wiersze = new ArrayList<>();
        for (String akapit : akapity) {
            if (!wiersze.isEmpty()) {
                wiersze.add(new Dialog.Wiersz("", Paleta.TEKST));
            }
            wiersze.add(new Dialog.Wiersz(akapit, Paleta.TEKST));
        }
        dialog = new Dialog(wiersze, tytul,
                List.of(new Dialog.Opcja("Rozumiem", "Zamknij i graj dalej.", true, this::zamknijDialog)),
                this::zamknijDialog);
    }

    private void porzucNowaGre() {
        dialog = null;
        gra = null;
        tryb = Tryb.MENU;
    }

    private void kontynuuj() {
        var wczytana = ZapisGry.wczytaj(plikZapisu);
        if (wczytana.isEmpty() || wczytana.get().czyKoniecGry() || wczytana.get().getDzien() == null) {
            komunikat = "Nie udało się wczytać zapisu (pochodzi z innej wersji gry albo jest uszkodzony).";
            return;
        }
        komunikat = null;
        gra = wczytana.get();
        tryb = Tryb.PORANEK;
        aktor = null;
        cel = null;
        przewiniecie = 0;
        radaPoranna();
    }

    private void zapisz() {
        // Zapis powstaje tylko rano, między rozkazami; dzień w toku odtworzy się z tego samego ziarna.
        if (gra == null || gra.czyKoniecGry() || gra.getDzien() == null
                || gra.getDzien().getFaza() != DzienGry.Faza.PORANEK) {
            return;
        }
        try {
            ZapisGry.zapisz(gra, plikZapisu);
        } catch (IOException | RuntimeException e) {
            komunikat = "Nie udało się zapisać gry: " + e.getMessage();
        }
    }

    private void wyjdzDoMenu() {
        zapisz();
        dialog = null;
        gra = null;
        tryb = Tryb.MENU;
    }

    private void wyjdzZGry() {
        zapisz();
        koniec = true;
    }

    private void rozegrajDzien() {
        przewiniecie = 0;
        gra.rozegrajPoranek();
        Walka walka = gra.rozpocznijObrone();
        if (walka == null) {
            zakonczDzien();
            return;
        }
        tryb = Tryb.WALKA;
        auto = false;
        aktor = null;
        cel = null;
        if (gra.czyTrwaSamouczek() && !radaWalkiPokazana) {
            radaWalkiPokazana = true;
            pokazRade("Doradca · pierwsza walka", Doradca.walka());
        }
    }

    private void krokWalki() {
        Walka walka = gra.getDzien().getWalka();
        if (walka == null || walka.czyZakonczona()) {
            return;
        }
        przewiniecie = 0;
        pokazRuch(gra.krokWalki());
    }

    private void przewinWalke() {
        Walka walka = gra.getDzien().getWalka();
        List<Zdarzenie> ostatnie = List.of();
        while (walka != null && !walka.czyZakonczona()) {
            ostatnie = gra.krokWalki();
        }
        przewiniecie = 0;
        pokazRuch(ostatnie);
    }

    /** Zapamiętuje, kto ostatnio działał i kogo trafił, żeby podświetlić ich na scenie. */
    private void pokazRuch(List<Zdarzenie> zdarzenia) {
        aktor = null;
        cel = null;
        for (Zdarzenie zdarzenie : zdarzenia) {
            switch (zdarzenie) {
                case Zdarzenie.Atak atak -> {
                    aktor = atak.kto();
                    cel = atak.cel();
                }
                case Zdarzenie.Kradziez kradziez -> aktor = kradziez.kto();
                case Zdarzenie.Ucieczka ucieczka -> aktor = aktor == null ? ucieczka.kto() : aktor;
                case Zdarzenie.StrataKolejki strata -> aktor = strata.kto();
                default -> {
                }
            }
        }
    }

    private void zakonczDzien() {
        gra.zakonczDzien();
        aktor = null;
        cel = null;
        auto = false;
        przewiniecie = 0;
        if (gra.czyKoniecGry()) {
            tryb = Tryb.KONIEC_GRY;
            ZapisGry.usun(plikZapisu);
            dialog = new Dialog("Loch upadł",
                    List.of("Twoje panowanie trwało " + gra.getNumerDnia() + " dni.",
                            "Ziarno tej rozgrywki: " + gra.getZiarno()),
                    List.of(new Dialog.Opcja("Nowa gra", "Zacznij od nowa z innym losowaniem.", true, () -> {
                                dialog = null;
                                tryb = Tryb.MENU;
                                nowaGra();
                            }),
                            new Dialog.Opcja("Menu główne", "Wróć do ekranu tytułowego.", true, () -> {
                                dialog = null;
                                gra = null;
                                tryb = Tryb.MENU;
                            })),
                    null);
        } else {
            tryb = Tryb.WIECZOR;
            dialogPodsumowanie();
        }
    }

    /** Wieczorne podsumowanie: bilans złota, wynik obrony i zmiany w załodze. */
    private void dialogPodsumowanie() {
        BilansDnia bilans = gra.getDzien().getBilans();
        Loch loch = gra.getLoch();
        List<Dialog.Wiersz> wiersze = new ArrayList<>();
        String rada = gra.czyTrwaSamouczek() ? Doradca.wieczor(gra.getNumerDnia()) : null;
        if (rada != null) {
            wiersze.add(new Dialog.Wiersz("Doradca: " + rada, Paleta.TEKST));
            wiersze.add(new Dialog.Wiersz("", Paleta.TEKST));
        }
        int zmiana = bilans.getZlotoWieczorem() - bilans.getZlotoRano();
        wiersze.add(new Dialog.Wiersz("Skarbiec: " + bilans.getZlotoRano() + " → " + bilans.getZlotoWieczorem()
                + " złota (" + (zmiana >= 0 ? "+" : "") + zmiana + ")", Paleta.ZLOTO));
        wiersze.add(new Dialog.Wiersz("Przychody +" + bilans.przychody() + ": dochód " + bilans.getDochod()
                + ", kopalnia " + bilans.getKopalnia(), Paleta.ZIELEN));
        List<String> wydatki = new ArrayList<>();
        pozycja(wydatki, "pensje ministrów", bilans.getPensje());
        pozycja(wydatki, "rekrutacja", bilans.getRekrutacja());
        pozycja(wydatki, "rozkazy", bilans.getRozkazy());
        pozycja(wydatki, "łapówka", bilans.getLapowka());
        pozycja(wydatki, "kradzieże", bilans.getSkradzione());
        pozycja(wydatki, "plądrowanie", bilans.getSpladrowane());
        pozycja(wydatki, "żołd", bilans.getZold());
        wiersze.add(new Dialog.Wiersz("Wydatki -" + bilans.wydatki()
                + (wydatki.isEmpty() ? "" : ": " + String.join(", ", wydatki)), Paleta.CZERWIEN));

        String obrona = switch (bilans.getObrona()) {
            case SPOKOJ -> "Obrona: spokojny dzień, nikt nie zaatakował.";
            case PRZEKUPSTWO -> "Obrona: bohaterowie wzięli łapówkę i odeszli.";
            case WYGRANA -> "Obrona: loch obroniony.";
            case PRZEGRANA -> "Obrona: PRZEGRANA. Porażki z rzędu: " + loch.getPorazkiZRzedu() + " z "
                    + Loch.PORAZKI_DO_UPADKU + ".";
            case BEZ_OBRONCOW -> "Obrona: nikt nie bronił lochu. Porażki z rzędu: " + loch.getPorazkiZRzedu()
                    + " z " + Loch.PORAZKI_DO_UPADKU + ".";
            case NIEROZEGRANA -> "Obrona: nie doszło do niej.";
        };
        boolean porazka = bilans.getObrona() == BilansDnia.Obrona.PRZEGRANA
                || bilans.getObrona() == BilansDnia.Obrona.BEZ_OBRONCOW;
        wiersze.add(new Dialog.Wiersz(obrona, porazka ? Paleta.CZERWIEN : Paleta.TEKST));

        List<String> zaloga = new ArrayList<>();
        lista(zaloga, "zatrudnieni", bilans.getZatrudnieni());
        lista(zaloga, "polegli", bilans.getPolegli());
        lista(zaloga, "odeszli", bilans.getOdeszli());
        lista(zaloga, "bez żołdu", bilans.getNieoplaceni());
        lista(zaloga, "awans", bilans.getAwanse());
        wiersze.add(new Dialog.Wiersz("Załoga: " + loch.getListaPotworow().size() + " z " + Loch.POJEMNOSC
                + (zaloga.isEmpty() ? ", bez zmian." : ". " + String.join(". ", zaloga) + "."), Paleta.TEKST));
        if (bilans.getWydobycieNaJutro() > 0) {
            wiersze.add(new Dialog.Wiersz("Jutro rano z kopalni: +" + bilans.getWydobycieNaJutro() + " złota.",
                    Paleta.TURKUS));
        }
        dialog = new Dialog(wiersze, "Podsumowanie dnia " + gra.getNumerDnia(),
                List.of(new Dialog.Opcja("Następny dzień", "Zaczyna się kolejny poranek.", true, () -> {
                            dialog = null;
                            nastepnyDzien();
                        }),
                        new Dialog.Opcja("Obejrzyj loch", "Zamknij podsumowanie i zostań przy wieczornym widoku.",
                                true, this::zamknijDialog)),
                this::zamknijDialog);
    }

    private static void pozycja(List<String> pozycje, String nazwa, int kwota) {
        if (kwota > 0) {
            pozycje.add(nazwa + " " + kwota);
        }
    }

    private static void lista(List<String> czesci, String nazwa, List<String> imiona) {
        if (!imiona.isEmpty()) {
            czesci.add(Character.toUpperCase(nazwa.charAt(0)) + nazwa.substring(1) + ": " + String.join(", ", imiona));
        }
    }

    private void nastepnyDzien() {
        gra.rozpocznijDzien();
        tryb = Tryb.PORANEK;
        przewiniecie = 0;
        zapisz();
        radaPoranna();
    }

    // ================= okna rozkazów =================

    private void zamknijDialog() {
        dialog = null;
    }

    private String wstepRozkazow() {
        return "Pozostałe rozkazy na dziś: " + gra.getDzien().getPozostaleRozkazy() + ". Złoto w skarbcu: "
                + gra.getLoch().getSkarbiec().getZloto() + ".";
    }

    private Dialog.Opcja opcjaRozkazu(String etykieta, String szczegol, Rozkaz rozkaz) {
        String powod = gra.powodOdmowy(rozkaz);
        int koszt = gra.koszt(rozkaz);
        String dopisek = powod != null ? " · " + powod : koszt > 0 ? " · koszt " + koszt + " złota" : "";
        return new Dialog.Opcja(etykieta, szczegol + dopisek, powod == null, () -> {
            gra.wykonajRozkaz(rozkaz);
            zapisz();
            zamknijDialog();
        });
    }

    private void dialogMinistrowie() {
        DzienGry dzien = gra.getDzien();
        List<Dialog.Opcja> opcje = new ArrayList<>();
        for (MinisterFinansow kandydat : dzien.getKandydaciFinansow()) {
            opcje.add(opcjaRozkazu("Finanse: " + kandydat.getImie() + " · " + kandydat.nazwaRodzaju(),
                    "pensja " + kandydat.getPensja() + " · negocjacje -" + procent(kandydat.getNegocjacje()) + "%",
                    new Rozkaz.ZmianaMinistraFinansow(kandydat)));
        }
        for (MinisterPracy kandydat : dzien.getKandydaciPracy()) {
            opcje.add(opcjaRozkazu("Kadry: " + kandydat.getImie() + " · " + kandydat.nazwaRodzaju(),
                    "pensja " + kandydat.getPensja() + " · werbunek +" + kandydat.getWerbunek() + " · kopalnia +"
                            + procent(kandydat.getNadzorKopalni()) + "%",
                    new Rozkaz.ZmianaMinistraPracy(kandydat)));
        }
        dialog = new Dialog("Zmiana ministra",
                List.of(wstepRozkazow(), "Nowy minister obejmuje urząd od jutra. Kandydaci zmieniają się co dzień."),
                opcje, this::zamknijDialog);
    }

    private void dialogBudzet() {
        List<Dialog.Opcja> opcje = new ArrayList<>();
        for (int procent : new int[] {0, 25, 50, 75, 100}) {
            String opis = procent == 0 ? "Wstrzymaj rekrutację: cały skarbiec zostaje na żołd i ulepszenia."
                    : procent == 100 ? "Bez ograniczeń: o budżecie decyduje sam Minister Finansów."
                    : "Minister Finansów odda na rekrutację najwyżej " + procent + "% skarbca.";
            opcje.add(opcjaRozkazu("Limit rekrutacji " + procent + "%", opis, new Rozkaz.LimitRekrutacji(procent)));
        }
        dialog = new Dialog("Budżet rekrutacji",
                List.of(wstepRozkazow(), "Obecny limit: " + gra.getLoch().getLimitRekrutacji() + "% skarbca."),
                opcje, this::zamknijDialog);
    }

    private void dialogPostawa() {
        List<Dialog.Opcja> opcje = new ArrayList<>();
        for (Postawa postawa : Postawa.values()) {
            opcje.add(opcjaRozkazu(postawa.nazwa(), postawa.opis(), new Rozkaz.ZmianaPostawy(postawa)));
        }
        dialog = new Dialog("Postawa wobec ataku",
                List.of(wstepRozkazow(), gra.getDzien().getRaport().opis()), opcje, this::zamknijDialog);
    }

    private void dialogUlepszenia() {
        Loch loch = gra.getLoch();
        List<Dialog.Opcja> opcje = new ArrayList<>();
        for (Ulepszenie ulepszenie : Ulepszenie.values()) {
            opcje.add(opcjaRozkazu(ulepszenie.nazwa() + " · poziom " + loch.poziom(ulepszenie) + "/"
                    + Ulepszenie.MAX_POZIOM, ulepszenie.opis(), new Rozkaz.ZakupUlepszenia(ulepszenie)));
        }
        dialog = new Dialog("Ulepszenia lochu", List.of(wstepRozkazow()), opcje, this::zamknijDialog);
    }

    private void dialogPotwory() {
        List<Dialog.Opcja> opcje = new ArrayList<>();
        for (Potwor potwor : gra.getLoch().getListaPotworow()) {
            opcje.add(new Dialog.Opcja(Sprite.emoji(potwor) + " " + potwor.getImie() + " · " + potwor.nazwaRodzaju()
                    + " · poziom " + potwor.getPoziom(),
                    "siła " + potwor.getSilaAtaku() + " · życie " + potwor.getMaxPunktyZycia() + " · morale "
                            + potwor.getMorale() + "/" + potwor.getMaxMorale() + " · żołd " + potwor.getZold(),
                    true, () -> dialogPotwor(potwor)));
        }
        if (opcje.isEmpty()) {
            opcje.add(new Dialog.Opcja("Loch jest pusty", "Nie masz żadnego potwora.", false, () -> {
            }));
        }
        dialog = new Dialog("Potwory w lochu",
                List.of(wstepRozkazow(), "Wybierz potwora, żeby dać mu premię albo go zwolnić."),
                opcje, this::zamknijDialog);
    }

    private void dialogPotwor(Potwor potwor) {
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(opcjaRozkazu("Premia", "Morale rośnie o 30% wartości maksymalnej.", new Rozkaz.Premia(potwor)));
        opcje.add(opcjaRozkazu("Zwolnij", "Potwór natychmiast opuszcza loch i nie pobiera już żołdu.",
                new Rozkaz.Zwolnienie(potwor)));
        opcje.add(new Dialog.Opcja("Wróć do listy", "Bez wydawania rozkazu.", true, this::dialogPotwory));
        dialog = new Dialog(potwor.getImie() + " · " + potwor.nazwaRodzaju(),
                List.of(wstepRozkazow(), "Siła " + potwor.getSilaAtaku() + ", życie " + potwor.getMaxPunktyZycia()
                        + ", morale " + potwor.getMorale() + "/" + potwor.getMaxMorale() + ", żołd "
                        + potwor.getZold() + ", poziom " + potwor.getPoziom() + "."),
                opcje, this::dialogPotwory);
    }

    /** Pomoc: w samouczku najpierw bieżąca rada doradcy, potem strony z zasadami. */
    private void dialogPomoc() {
        boolean wGrze = gra != null && tryb != Tryb.MENU;
        List<String> rada = wGrze && gra.czyTrwaSamouczek() && tryb == Tryb.PORANEK
                ? Doradca.poranek(gra.getNumerDnia()) : List.of();
        if (rada.isEmpty()) {
            strona(0, "Zamknij", this::zamknijDialog, this::zamknijDialog);
            return;
        }
        List<Dialog.Wiersz> wiersze = new ArrayList<>();
        for (String akapit : rada) {
            if (!wiersze.isEmpty()) {
                wiersze.add(new Dialog.Wiersz("", Paleta.TEKST));
            }
            wiersze.add(new Dialog.Wiersz(akapit, Paleta.TEKST));
        }
        dialog = new Dialog(wiersze, "Doradca · dzień " + gra.getNumerDnia(),
                List.of(new Dialog.Opcja("Rozumiem", "Zamknij i graj dalej.", true, this::zamknijDialog),
                        new Dialog.Opcja("Zasady gry", "Cztery krótkie strony o tym, jak działa loch.", true,
                                () -> strona(0, "Zamknij", this::zamknijDialog, this::zamknijDialog))),
                this::zamknijDialog);
    }
}
