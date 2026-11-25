package wladcalochow.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import wladcalochow.Aktualizacje;
import wladcalochow.Uruchamiacz;
import wladcalochow.Wersja;
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
import wladcalochow.model.strategia.CechaMinistra;
import wladcalochow.model.strategia.OcenaAtaku;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.RaportZwiadu;
import wladcalochow.model.strategia.RodzajRozkazu;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.model.strategia.Ulepszenie;
import wladcalochow.model.strategia.WadaMinistra;
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

    private static final DateTimeFormatter FORMAT_DATY =
            DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final Konsola terminal;
    private final Path folderZapisow;
    /** Slot bieżącej rozgrywki (1..10) albo 0, gdy gra jeszcze nie ma slotu. */
    private int slot;
    /** Gdy Claude gra za Władcę: most do plików, przez które dostaje stan i odsyła decyzje. */
    private final MostClaude most;
    private boolean claudeGra;
    /** Identyfikator prośby, na którą czekamy (null: na nic nie czekamy). */
    private String czekamNa;
    private boolean czekamNaStart;
    private final Long ziarnoStartowe;
    private final List<Strefa> strefy = new ArrayList<>();
    private final Map<Character, Runnable> skroty = new HashMap<>();
    private Runnable akcjaEnter;

    private Gra gra;
    private Tryb tryb = Tryb.MENU;
    private Dialog dialog;
    private Ekran poprzedni;
    private boolean koniec;
    /** Walka toczy się sama. Klawisz A przełącza na ruchy po Enterze i z powrotem. */
    private boolean auto = true;
    private Istota aktor;
    private Istota cel;
    private int przewiniecie;
    /** Ostatnia znana pozycja kursora myszy (do podświetlania przycisków); -1: nieznana. */
    private int myszX = -1;
    private int myszY = -1;
    private static final int PRZEWIJANIE_KOLKIEM = 3;
    /** Czy ekran trzeba przerysować (coś się zmieniło od ostatniej klatki). */
    private boolean doNarysowania = true;
    private String komunikat;
    private boolean radaWalkiPokazana;
    private volatile String nowaWersja;

    public Aplikacja(Path folderZapisow, Long ziarnoStartowe, boolean sprawdzajAktualizacje) throws IOException {
        this(new TerminalGry(), folderZapisow, ziarnoStartowe, folderZapisow.resolveSibling("claude"));
        if (sprawdzajAktualizacje) {
            Aktualizacje.sprawdzWTle(Wersja.numer())
                    .thenAccept(wynik -> wynik.ifPresent(wersja -> nowaWersja = wersja));
        }
    }

    /**
     * Rozgrywa kilka dni na terminalu bez ekranu i sprawdza zapis gry. Rzuca wyjątek, gdy coś nie działa.
     * Używane przez opcję --sprawdz, którą wywołuje instalator.
     */
    public static void autotest() throws IOException {
        Path folder = Files.createTempDirectory("wladca-lochow-autotest");
        Path zapis = ZapisGry.plikSlotu(folder, 1);
        try {
            Aplikacja aplikacja = new Aplikacja(new KonsolaBezEkranu(MIN_SZER + 16, MIN_WYS + 8), folder, 1L);
            aplikacja.rysuj();
            Gra stan = Gra.nowa(1L);
            stan.rozpocznij(stan.getStartowiFinansow().get(0), stan.getStartowiPracy().get(0));
            aplikacja.pokazGre(stan, 1);
            aplikacja.rysuj();
            for (int krok = 0; krok < 5000 && stan.getNumerDnia() < 6 && !stan.czyKoniecGry(); krok++) {
                aplikacja.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER));
                aplikacja.rysuj();
            }
            if (stan.getNumerDnia() < 6 && !stan.czyKoniecGry()) {
                throw new IllegalStateException("Rozgrywka nie posuwa się naprzód");
            }
            if (ZapisGry.wczytaj(zapis).isEmpty()) {
                throw new IllegalStateException("Zapis gry nie daje się odczytać");
            }
        } finally {
            Files.deleteIfExists(zapis);
            Files.deleteIfExists(folder);
        }
    }

    Aplikacja(Konsola terminal, Path folderZapisow, Long ziarnoStartowe) {
        this(terminal, folderZapisow, ziarnoStartowe, folderZapisow.resolve("claude"));
    }

    Aplikacja(Konsola terminal, Path folderZapisow, Long ziarnoStartowe, Path folderClaude) {
        this.terminal = terminal;
        this.folderZapisow = folderZapisow;
        this.most = new MostClaude(folderClaude);
        this.ziarnoStartowe = ziarnoStartowe;
    }

    public void uruchom() throws IOException {
        try {
            // Czas gry (krok walki, sama gra) płynie co okres, także gdy ruch myszy ciągle przysyła zdarzenia.
            // Ekran rysuje się tylko po zmianie: ruch myszy, który niczego nie podświetla, nie kosztuje nic.
            long ostatniTik = System.currentTimeMillis();
            doNarysowania = true;
            while (!koniec) {
                if (doNarysowania) {
                    doNarysowania = false;
                    rysuj();
                }
                long okres = walkaTrwaSama() ? 450 : 300;
                long minelo = System.currentTimeMillis() - ostatniTik;
                Wejscie wejscie = terminal.czytaj(Math.max(1, okres - minelo));
                // Ruchy myszy przychodzą seriami: zbieramy całą serię i rysujemy raz, na końcu.
                while (wejscie instanceof Wejscie.Ruch) {
                    obsluz(wejscie);
                    wejscie = terminal.czytaj(1);
                }
                switch (wejscie) {
                    case Wejscie.Brak brak -> {
                    }
                    case Wejscie.Koniec k -> {
                        zapiszPrzyWyjsciu();
                        koniec = true;
                    }
                    default -> {
                        obsluz(wejscie);
                        doNarysowania = true;
                    }
                }
                long teraz = System.currentTimeMillis();
                if (!koniec && teraz - ostatniTik >= okres) {
                    ostatniTik = teraz;
                    tik(teraz);
                    doNarysowania = true;
                }
            }
        } finally {
            terminal.close();
        }
    }

    // ================= wejście =================

    // ---- dostęp dla testów układu ekranu ----

    /** Pokazuje podany stan gry tak, jakby został właśnie wczytany (poranek). */
    void pokazGre(Gra stan) {
        pokazGre(stan, 0);
    }

    /** Jak wyżej, a dalsze zapisy tej rozgrywki trafiają do podanego slotu (0: bez zapisu). */
    void pokazGre(Gra stan, int numerSlotu) {
        slot = numerSlotu;
        gra = stan;
        tryb = Tryb.PORANEK;
        dialog = null;
        aktor = null;
        cel = null;
        przewiniecie = 0;
    }

    Gra gra() {
        return gra;
    }

    /** Udaje, że sprawdzanie aktualizacji znalazło nową wersję. */
    void ustawNowaWersje(String wersja) {
        nowaWersja = wersja;
    }

    Ekran klatka() {
        return poprzedni;
    }

    boolean czyKoniec() {
        return koniec;
    }

    boolean czyDialog() {
        return dialog != null;
    }

    List<Character> dostepneSkroty() {
        return new ArrayList<>(skroty.keySet());
    }

    /** Numer strefy klikalnej pod podanym punktem w ostatniej klatce albo -1. */
    private int strefaPod(int x, int y) {
        for (int i = strefy.size() - 1; i >= 0; i--) {
            if (strefy.get(i).zawiera(x, y)) {
                return i;
            }
        }
        return -1;
    }

    /** Prostokąty stref klikalnych bieżącej klatki: x, y, szerokość, wysokość. */
    List<int[]> prostokatyStref() {
        return strefy.stream().map(s -> new int[] {s.x(), s.y(), s.w(), s.h()}).toList();
    }

    void obsluz(Wejscie wejscie) {
        if (wejscie instanceof Wejscie.Ruch ruch) {
            // Najechanie: przycisk pod kursorem się podświetla, a w oknie zaznacza się opcja pod kursorem.
            // Przerysowanie tylko wtedy, gdy kursor wszedł na inny przycisk albo inną opcję.
            int przed = strefaPod(myszX, myszY);
            myszX = ruch.x();
            myszY = ruch.y();
            boolean zmiana = strefaPod(myszX, myszY) != przed;
            if (dialog != null) {
                zmiana |= dialog.najedz(ruch.x(), ruch.y());
            }
            doNarysowania |= zmiana;
            return;
        }
        if (wejscie instanceof Wejscie.Kolko kolko) {
            // Kółko: w oknie przesuwa zaznaczenie, poza oknem przewija dziennik (w górę = starsze wpisy).
            if (dialog != null) {
                dialog.przewin(kolko.kierunek());
            } else {
                przewiniecie = Math.max(0, przewiniecie - kolko.kierunek() * PRZEWIJANIE_KOLKIEM);
            }
            return;
        }
        if (wejscie instanceof Wejscie.Klik klik) {
            myszX = klik.x();
            myszY = klik.y();
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

    void rysuj() {
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
        String wersja = "wersja " + Wersja.numer() + " ";
        e.pisz(e.szer - Ekran.szerokosc(wersja), 0, wersja, Paleta.TEKST_SLABY);
        String dostepna = nowaWersja;
        if (dostepna != null) {
            String wiesc = "Jest nowa wersja " + dostepna + ". Naciśnij A, żeby zaktualizować.";
            e.pisz((e.szer - Ekran.szerokosc(wiesc)) / 2, ty + 7, wiesc, Paleta.ZIELEN, Ekran.BEZ_ZMIAN, true);
        }

        int sw = Math.min(e.szer - 4, 120);
        Scena.rysuj(e, (e.szer - sw) / 2, ty + 8, sw, Scena.Pora.NOC, List.of(), List.of(), null, null);

        List<ZapisGry.Slot> sloty = ZapisGry.sloty(folderZapisow);
        boolean doKontynuacji = ZapisGry.ostatniDoKontynuacji(sloty).isPresent();
        boolean jestZapis = sloty.stream().anyMatch(ZapisGry.Slot::zajety);
        List<Guzik> guziki = new ArrayList<>();
        guziki.add(new Guzik('k', "Kontynuuj", false, doKontynuacji, this::kontynuuj));
        if (dostepna != null) {
            guziki.add(new Guzik('a', "Aktualizuj", false, true, this::aktualizuj, true));
        }
        guziki.add(new Guzik('w', "Wczytaj", false, jestZapis, this::dialogWczytaj));
        guziki.add(new Guzik('c', claudeGra ? "Claude: wł." : "Claude: wył.", false, true, this::przelaczClaude));
        guziki.add(new Guzik('o', "O grze", false, true, this::dialogOGrze));
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
        if (tryb == Tryb.PORANEK) {
            // Na pasku nie ma już miejsca, więc Claude'a włącza i wyłącza sam klawisz C (podpowiedź w dzienniku).
            skroty.put('c', this::przelaczClaude);
        }
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
                if (czekamNa != null) {
                    yield "Czekam na decyzje Claude'a · C: graj sam";
                }
                if (claudeGra) {
                    yield "Gra toczy się sama · C: przejmij grę";
                }
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
            int dlaObroncow = obroncyWalki(walka).size() + 2;
            int wysBohaterow = Math.max(3, Math.min(bohaterowie.size() + 2, h - dlaObroncow));
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

        // Jedna ramka na ministrów i ustawienia lochu: 10 wierszy, każdy tekst ma swoje miejsce.
        MinisterFinansow finansow = loch.getMinisterFinansow();
        MinisterPracy pracy = loch.getMinisterPracy();
        e.ramka(x, y, w, 12, Paleta.RAMKA, Paleta.PANEL, "Ministrowie i loch", Paleta.ZLOTO);
        int tx = x + 2;
        int tw = w - 4;
        // Stażysta (darmowy zastępca po ministrze, który odszedł) jest czerwony: to sygnał, że trzeba kogoś zatrudnić.
        boolean stazystaF = finansow.getPensja() == 0;
        boolean stazystaP = pracy.getPensja() == 0;
        linia(e, tx, y + 1, tw, Sprite.FINANSE + " Finanse: " + finansow.getImie(),
                stazystaF ? Paleta.CZERWIEN : Paleta.TEKST, true);
        linia(e, tx + 3, y + 2, tw - 3, stazystaF ? "zastępstwo, bez cech: wymień (M)"
                : finansow.nazwaRodzaju() + ", pensja " + finansow.getPensja() + ", zniżki "
                + procent(finansow.getNegocjacje()) + "%", stazystaF ? Paleta.CZERWIEN : Paleta.TEKST_SLABY, false);
        if (gra.getNastepnyMinisterFinansow() != null) {
            linia(e, tx + 3, y + 3, tw - 3, "od jutra: " + gra.getNastepnyMinisterFinansow().getImie(),
                    Paleta.POMARANCZ, false);
        } else {
            cechaIWada(e, tx + 3, y + 3, tw - 3, finansow.getCecha(), finansow.getWada());
        }
        linia(e, tx, y + 4, tw, Sprite.KADRY + " Kadry: " + pracy.getImie(),
                stazystaP ? Paleta.CZERWIEN : Paleta.TEKST, true);
        linia(e, tx + 3, y + 5, tw - 3, stazystaP ? "zastępstwo, bez cech: wymień (M)"
                : pracy.nazwaRodzaju() + ", pensja " + pracy.getPensja(),
                stazystaP ? Paleta.CZERWIEN : Paleta.TEKST_SLABY, false);
        linia(e, tx + 3, y + 6, tw - 3, "werbunek +" + pracy.getWerbunek() + ", kopalnia +"
                + procent(pracy.getNadzorKopalni()) + "%", Paleta.TEKST_SLABY, false);
        if (gra.getNastepnyMinisterPracy() != null) {
            linia(e, tx + 3, y + 7, tw - 3, "od jutra: " + gra.getNastepnyMinisterPracy().getImie(),
                    Paleta.POMARANCZ, false);
        } else {
            cechaIWada(e, tx + 3, y + 7, tw - 3, pracy.getCecha(), pracy.getWada());
        }
        if (loch.getWydobycieDoOdebrania() > 0) {
            linia(e, tx, y + 8, tw, "Z kopalni jutro rano: +" + loch.getWydobycieDoOdebrania() + " złota",
                    Paleta.TURKUS, false);
        }
        // Dzienne koszty jedną liczbą (pensje i żołd): czerwone, gdy skarbiec ich nie pokrywa.
        int koszty = gra.kosztyDnia();
        int px = e.pisz(tx, y + 9, "Koszty dnia ", Paleta.TEKST_SLABY);
        px = e.pisz(px, y + 9, String.valueOf(koszty),
                koszty > loch.getSkarbiec().getZloto() ? Paleta.CZERWIEN : Paleta.ZLOTO, Ekran.BEZ_ZMIAN, true);
        String limit = " · rekrutacja do " + loch.getLimitRekrutacji() + "%";
        if (px - tx + Ekran.szerokosc(limit) <= tw) {
            e.pisz(px, y + 9, limit, Paleta.TEKST_SLABY);
        }
        // Poziomy ulepszeń liczbami (pięć kratek na każde ulepszenie nie mieści się w najwęższym oknie).
        int ux = tx;
        for (Ulepszenie ulepszenie : Ulepszenie.values()) {
            ux = e.pisz(ux, y + 10, skrotUlepszenia(ulepszenie) + " ", Paleta.TEKST_SLABY);
            int poziom = loch.poziom(ulepszenie);
            ux = e.pisz(ux, y + 10, poziom + "/" + Ulepszenie.MAX_POZIOM, poziom > 0 ? Paleta.TURKUS : Paleta.TEKST_SLABY);
            ux += 2;
        }

        List<Potwor> potwory = wedlugMocy(loch.getListaPotworow());
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

    /** Jeden wiersz panelu. Gdyby tekst się nie zmieścił, dostanie wielokropek, który wyłapują testy układu. */
    private static void linia(Ekran e, int x, int y, int maks, String tekst, int kolor, boolean pogrubiony) {
        e.pisz(x, y, Ekran.przytnij(tekst, maks), kolor, Ekran.BEZ_ZMIAN, pogrubiony);
    }

    /** Cecha (zielona) i wada (czerwona) ministra w jednej linii panelu; nic, gdy minister ich nie ma. */
    private static void cechaIWada(Ekran e, int x, int y, int maks, CechaMinistra cecha, WadaMinistra wada) {
        String c = cecha == null ? "" : "+" + cecha.nazwa();
        String wd = wada == null ? "" : "−" + wada.nazwa();
        if (Ekran.szerokosc(c) + 1 + Ekran.szerokosc(wd) > maks) {
            return;
        }
        int px = e.pisz(x, y, c, Paleta.ZIELEN);
        e.pisz(c.isEmpty() ? px : px + 1, y, wd, Paleta.CZERWIEN);
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
        } else if (istota instanceof Potwor ranny && ranny.czyRanny()) {
            znacznik = "ranny";
            kolorZnacznika = Paleta.CZERWIEN;
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

    /** Jedna linia dziennika na ekranie: tekst, kolor, pogrubienie i tło całej linii (BEZ_ZMIAN: bez tła). */
    record WierszDziennika(String etykieta, String tekst, int kolor, boolean gruby, int tlo) {
    }

    /** Szerokość kolumny z etykietami w dzienniku („Kandydaci” + odstęp). */
    private static final int KOLUMNA_ETYKIET = 12;

    /**
     * Dziennik do czytania, a nie ściana tekstu:
     * - każdy dzień zaczyna się wyraźnym paskiem;
     * - ciosy z zakończonych walk są zwinięte do jednej linii (pełne ciosy widać tylko w trwającej walce);
     * - zdarzenia, które zmieniają loch na dłużej (ton ALARM: odejście ministra, przegrana, ranni),
     *   mają czerwone tło i wykrzyknik, żeby nie dało się ich przegapić.
     */
    List<WierszDziennika> linieDziennika(List<Gra.WpisDziennika> wpisy, int szer) {
        List<WierszDziennika> linie = new ArrayList<>();
        int od = Math.max(0, wpisy.size() - 250);
        // Ostatni blok ciosów jest rozwinięty tylko wtedy, gdy walka właśnie trwa na ekranie.
        int ostatniBlok = -1;
        if (tryb == Tryb.WALKA) {
            for (int i = wpisy.size() - 1; i >= od && ostatniBlok < 0; i--) {
                if (wpisy.get(i).walka()) {
                    ostatniBlok = i;
                }
            }
            while (ostatniBlok > od && wpisy.get(ostatniBlok - 1).walka()) {
                ostatniBlok--;
            }
        }
        int tloAlarmu = Paleta.mieszaj(Paleta.PANEL, Paleta.CZERWIEN, 0.32);
        for (int i = od; i < wpisy.size(); i++) {
            Gra.WpisDziennika wpis = wpisy.get(i);
            if (wpis.walka() && (ostatniBlok < 0 || i < ostatniBlok)) {
                // Zwinięta walka: ile ciosów i rund, bez szczegółów.
                int ciosy = 0;
                int rundy = 0;
                int j = i;
                while (j < wpisy.size() && wpisy.get(j).walka() && (ostatniBlok < 0 || j < ostatniBlok)) {
                    if (wpisy.get(j).tekst().startsWith("Runda ")) {
                        rundy++;
                    } else {
                        ciosy++;
                    }
                    j++;
                }
                linie.add(new WierszDziennika("Walka", rundy + " rund, " + ciosy + " ruchów",
                        Paleta.TEKST_SLABY, false, Ekran.BEZ_ZMIAN));
                i = j - 1;
                continue;
            }
            String tekst = wpis.tekst();
            if (wpis.ton() == Zdarzenie.Ton.NAGLOWEK && tekst.startsWith("Dzień ")) {
                String pasek = " " + tekst.toUpperCase() + " ";
                linie.add(new WierszDziennika("", pasek, Paleta.ZLOTO, true, Paleta.NAGLOWEK));
                continue;
            }
            boolean alarm = wpis.ton() == Zdarzenie.Ton.ALARM;
            boolean naglowek = wpis.ton() == Zdarzenie.Ton.NAGLOWEK;
            // Ciosy trwającej walki zostają zdaniami; reszta to krótka etykieta i treść w dwóch kolumnach.
            SkrotDziennika.Skrot skrot = wpis.walka() ? new SkrotDziennika.Skrot("", tekst) : SkrotDziennika.skroc(tekst);
            int naTresc = Math.max(10, szer - KOLUMNA_ETYKIET);
            boolean pierwszy = true;
            for (String wiersz : Ekran.zawin(skrot.tresc(), naTresc)) {
                linie.add(new WierszDziennika(pierwszy ? skrot.etykieta() : "", wiersz,
                        alarm ? Paleta.TEKST : Paleta.ton(wpis.ton()), alarm || naglowek,
                        alarm ? tloAlarmu : Ekran.BEZ_ZMIAN));
                pierwszy = false;
            }
        }
        return linie;
    }

    private void dziennik(Ekran e, int x, int y, int w, int h) {
        e.ramka(x, y, w, h, Paleta.RAMKA, Paleta.PANEL, "Dziennik", Paleta.POMARANCZ);
        String podpowiedz = " " + Ekran.przytnij(coTeraz(), w - 17) + " ";
        e.pisz(x + w - Ekran.szerokosc(podpowiedz) - 2, y, podpowiedz, Paleta.ZLOTO, Ekran.BEZ_ZMIAN, true);
        int wiersze = h - 2;
        List<WierszDziennika> linie = linieDziennika(gra.getDziennik(), w - 4);
        int maksPrzewiniecie = Math.max(0, linie.size() - wiersze);
        przewiniecie = Math.max(0, Math.min(przewiniecie, maksPrzewiniecie));
        int koniecListy = linie.size() - przewiniecie;
        int start = Math.max(0, koniecListy - wiersze);
        for (int i = start; i < koniecListy; i++) {
            WierszDziennika l = linie.get(i);
            int wy = y + 1 + i - start;
            if (l.tlo() != Ekran.BEZ_ZMIAN) {
                e.wypelnij(x + 1, wy, w - 2, 1, l.tlo());
            }
            if (l.tlo() == Paleta.NAGLOWEK) {
                // Pasek dnia zaczyna się od lewej krawędzi, bez kolumny etykiet.
                e.pisz(x + 2, wy, l.tekst(), l.kolor(), Ekran.BEZ_ZMIAN, l.gruby());
                continue;
            }
            e.pisz(x + 2, wy, l.etykieta(), l.tlo() == Ekran.BEZ_ZMIAN ? Paleta.TEKST_SLABY : Paleta.TEKST,
                    Ekran.BEZ_ZMIAN, l.tlo() != Ekran.BEZ_ZMIAN);
            e.pisz(x + 2 + KOLUMNA_ETYKIET, wy, l.tekst(), l.kolor(), Ekran.BEZ_ZMIAN, l.gruby());
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
                guziki.add(new Guzik('\n', "Rozpocznij dzień", true, czekamNa == null, this::rozegrajDzien));
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
            case KONIEC_GRY -> {
                guziki.add(new Guzik('s', "Podsumowanie", false, true, this::dialogKoniec));
                guziki.add(new Guzik('h', "Pomoc", false, true, this::dialogPomoc));
                guziki.add(new Guzik('q', "Menu", false, true, this::wyjdzDoMenu));
                guziki.add(new Guzik('\n', "Nowa gra", true, true, this::nowaGra));
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
        boolean aktywny = czekamNa == null && (gra.getDzien().maRozkazy() || rodzaj == RodzajRozkazu.POTWORY);
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
            // Kursor nad aktywnym przyciskiem (i bez otwartego okna): przycisk jaśnieje.
            boolean podMysza = guzik.aktywny() && dialog == null && myszX >= gx && myszX < gx + w
                    && myszY >= y && myszY < y + 3;
            if (podMysza) {
                tlo = Paleta.mieszaj(tlo, 0xFFFFFF, 0.22);
            }
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

    /**
     * Aktualizacja: na Windows otwiera instalator w nowym oknie i zamyka grę, żeby dało się podmienić plik.
     * Na innych systemach pokazuje, skąd pobrać nową wersję.
     */
    private void aktualizuj() {
        // Instalator startuje tylko z prawdziwej gry, nigdy z testów na sztucznym terminalu.
        String blad = null;
        if (terminal instanceof TerminalGry) {
            java.util.Optional<String> wynik = Uruchamiacz.uruchomAktualizacje();
            if (wynik.isEmpty()) {
                wyjdzZGry();
                return;
            }
            blad = wynik.get();
        }
        List<String> tresc = new ArrayList<>();
        if (blad != null) {
            tresc.add("Nie udało się samemu uruchomić instalatora (" + blad + ").");
        }
        tresc.add("Na Windows zamknij grę i wklej do PowerShella:");
        tresc.add(Aktualizacje.POLECENIE_INSTALATORA);
        tresc.add("Na innych systemach pobierz nowy wladca-lochow.jar ze strony wydań:");
        tresc.add(Aktualizacje.STRONA_WYDAN);
        dialog = new Dialog("Nowa wersja " + nowaWersja,
                tresc,
                List.of(new Dialog.Opcja("Zamknij", "Wróć do menu.", true, this::zamknijDialog)),
                this::zamknijDialog);
    }

    /** Krótkie przedstawienie gry dla kogoś, kto widzi ją pierwszy raz. Kończy się propozycją nowej gry. */
    private void dialogOGrze() {
        strona(Doradca.O_GRZE, 0, "Nowa gra", this::nowaGra, this::zamknijDialog);
    }

    /** Nowa gra trafia do pierwszego wolnego slotu. Gdy wszystkie są zajęte, gracz wybiera, który zastąpić. */
    private void nowaGra() {
        komunikat = null;
        List<ZapisGry.Slot> sloty = ZapisGry.sloty(folderZapisow);
        var wolny = ZapisGry.wolnySlot(sloty);
        if (wolny.isPresent()) {
            nowaGraWSlocie(wolny.get().numer());
            return;
        }
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(Dialog.Opcja.info(0, NAGLOWKI_SLOTOW, null));
        for (ZapisGry.Slot zajety : sloty) {
            opcje.add(Dialog.Opcja.wiersz(0, komorkiSlotu(zajety),
                    List.of(Dialog.Linia.tekst("Ta rozgrywka zostanie zastąpiona nową.", Paleta.CZERWIEN)), true,
                    () -> nowaGraWSlocie(zajety.numer())));
        }
        dialog = Dialog.zLiniami("Nowa gra · wszystkie sloty zajęte",
                List.of(Dialog.Linia.tekst("Wybierz slot, którego zapis zastąpi nowa gra. Pozostałe zapisy zostaną "
                        + "bez zmian.", Paleta.TEKST)),
                opcje, this::zamknijDialog);
    }

    private void nowaGraWSlocie(int numer) {
        if (claudeGra) {
            // Claude gra bez samouczka i sam wybiera startowych ministrów.
            zacznijNowaGre(false, numer);
            return;
        }
        dialog = new Dialog("Nowa gra · slot " + numer,
                List.of("Samouczek prowadzi przez pierwsze 5 dni: możliwości pojawiają się po jednej, "
                        + "a doradca tłumaczy, co się dzieje."),
                List.of(new Dialog.Opcja("Z samouczkiem", "Zalecane za pierwszym razem.", true,
                                () -> zacznijNowaGre(true, numer)),
                        new Dialog.Opcja("Bez samouczka", "Wszystkie rozkazy dostępne od pierwszego dnia.", true,
                                () -> zacznijNowaGre(false, numer))),
                this::zamknijDialog);
    }

    private static final List<List<Dialog.Kawalek>> NAGLOWKI_SLOTOW =
            Wiersze.naglowki("Slot", "Stan", "Dzień", "Złoto", "Potwory", "Zapisano");

    /** Slot jako wiersz tabeli: numer, stan, dzień, złoto, potwory i data zapisu. */
    private static List<List<Dialog.Kawalek>> komorkiSlotu(ZapisGry.Slot s) {
        List<Dialog.Kawalek> numer = Wiersze.komorka(Wiersze.g(String.valueOf(s.numer()), Paleta.TEKST));
        List<Dialog.Kawalek> pusta = Wiersze.komorka("", Paleta.TEKST);
        if (!s.zajety()) {
            return Wiersze.wiersz(numer, Wiersze.komorka("pusty", Paleta.TEKST_SLABY), pusta, pusta, pusta, pusta);
        }
        List<Dialog.Kawalek> data = Wiersze.komorka(s.czas() == null ? "" : FORMAT_DATY.format(s.czas().toInstant()),
                Paleta.TEKST_SLABY);
        if (s.gra().isEmpty()) {
            return Wiersze.wiersz(numer, Wiersze.komorka("nie do odczytania", Paleta.CZERWIEN), pusta, pusta, pusta, data);
        }
        Gra g = s.gra().get();
        List<Dialog.Kawalek> stan = g.czyKoniecGry() ? Wiersze.komorka("przegrana", Paleta.CZERWIEN)
                : g.getDzien() == null ? Wiersze.komorka("przed startem", Paleta.TEKST_SLABY)
                : Wiersze.komorka("w toku", Paleta.ZIELEN);
        return Wiersze.wiersz(numer, stan, Wiersze.komorka(String.valueOf(g.getNumerDnia()), Paleta.TEKST),
                Wiersze.komorka(String.valueOf(g.getLoch().getSkarbiec().getZloto()), Paleta.ZLOTO),
                Wiersze.komorka(String.valueOf(g.getLoch().getListaPotworow().size()), Paleta.TEKST), data);
    }

    /** Lista slotów: rozgrywkę w toku można kontynuować, przegraną obejrzeć. */
    private void dialogWczytaj() {
        komunikat = null;
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(Dialog.Opcja.info(0, NAGLOWKI_SLOTOW, null));
        for (ZapisGry.Slot s : ZapisGry.sloty(folderZapisow)) {
            String szczegol = !s.zajety() ? "Pusty slot: nowa gra trafi do pierwszego wolnego."
                    : s.gra().isEmpty() ? "Zapis pochodzi z innej wersji gry albo jest uszkodzony."
                    : s.doKontynuacji() ? "Graj dalej od zapisanego poranka."
                    : "Rozgrywka zakończona: można obejrzeć jej ostatni dzień.";
            boolean aktywna = s.gra().isPresent() && (s.doKontynuacji() || s.gra().get().czyKoniecGry());
            opcje.add(Dialog.Opcja.wiersz(0, komorkiSlotu(s), List.of(Dialog.Linia.tekst(szczegol, Paleta.TEKST)),
                    aktywna, () -> wczytajSlot(s)));
        }
        dialog = Dialog.zLiniami("Wczytaj grę",
                List.of(Dialog.Linia.tekst("Gra nigdy sama nie usuwa zapisów. Przegrane rozgrywki zostają w swoich "
                        + "slotach.", Paleta.TEKST_SLABY)),
                opcje, this::zamknijDialog);
    }

    private void wczytajSlot(ZapisGry.Slot s) {
        dialog = null;
        komunikat = null;
        Gra wczytana = s.gra().orElse(null);
        if (wczytana == null) {
            komunikat = "Nie udało się wczytać slotu " + s.numer() + ".";
            return;
        }
        slot = s.numer();
        gra = wczytana;
        aktor = null;
        cel = null;
        przewiniecie = 0;
        if (wczytana.czyKoniecGry()) {
            tryb = Tryb.KONIEC_GRY;
            dialogKoniec();
        } else {
            tryb = Tryb.PORANEK;
            radaPoranna();
            poprosClaudeOPoranek();
        }
    }

    private void zacznijNowaGre(boolean samouczek, int numerSlotu) {
        long ziarno = ziarnoStartowe != null ? ziarnoStartowe : System.nanoTime();
        slot = numerSlotu;
        // Do wyboru ministrów w tle jest menu: nowa gra nie ma jeszcze dnia, więc pulpitu nie da się narysować.
        tryb = Tryb.MENU;
        gra = samouczek ? Gra.nowaZSamouczkiem(ziarno) : Gra.nowa(ziarno);
        if (claudeGra) {
            poprosClaudeOStart();
        } else if (samouczek) {
            strona(Doradca.STRONY, 0, "Wybierz ministrów", this::wybierzMinistraFinansow, this::porzucNowaGre);
        } else {
            wybierzMinistraFinansow();
        }
    }

    /** Strona wprowadzenia lub pomocy z przyciskami Dalej i Wstecz. */
    private void strona(List<Doradca.Strona> strony, int numer, String etykietaKonca, Runnable poZakonczeniu,
                        Runnable anuluj) {
        Doradca.Strona strona = strony.get(numer);
        boolean ostatnia = numer == strony.size() - 1;
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(ostatnia
                ? new Dialog.Opcja(etykietaKonca, "To już ostatnia strona.", true, poZakonczeniu)
                : new Dialog.Opcja("Dalej", "Następna strona: " + strony.get(numer + 1).tytul() + ".", true,
                        () -> strona(strony, numer + 1, etykietaKonca, poZakonczeniu, anuluj)));
        if (numer > 0) {
            opcje.add(new Dialog.Opcja("Wstecz", "Poprzednia strona.", true,
                    () -> strona(strony, numer - 1, etykietaKonca, poZakonczeniu, anuluj)));
        }
        dialog = Dialog.zLiniami(strona.tytul() + " · " + (numer + 1) + "/" + strony.size(),
                akapity(strona.akapity()), opcje, anuluj);
    }

    /**
     * Akapity tekstu z wyróżnieniami: początek do dwukropka (np. „Żołd:”) i fragmenty w **gwiazdkach**
     * są złote i pogrubione, żeby oko od razu łapało, o czym jest akapit. Między akapitami jest odstęp.
     */
    static List<Dialog.Linia> akapity(List<String> akapity) {
        List<Dialog.Linia> wynik = new ArrayList<>();
        for (String akapit : akapity) {
            if (!wynik.isEmpty()) {
                wynik.add(Dialog.Linia.pusta());
            }
            wynik.add(akapit(akapit));
        }
        return wynik;
    }

    static Dialog.Linia akapit(String tekst) {
        List<Dialog.Kawalek> k = new ArrayList<>();
        String reszta = tekst;
        int dwukropek = tekst.indexOf(": ");
        if (dwukropek > 0 && dwukropek <= 24 && !tekst.substring(0, dwukropek).contains(".")) {
            k.add(Wiersze.g(tekst.substring(0, dwukropek + 1), Paleta.ZLOTO));
            reszta = tekst.substring(dwukropek + 1);
        }
        String[] czesci = reszta.split("\\*\\*", -1);
        for (int i = 0; i < czesci.length; i++) {
            if (!czesci[i].isEmpty()) {
                k.add(i % 2 == 1 ? Wiersze.g(czesci[i], Paleta.ZLOTO) : Wiersze.k(czesci[i], Paleta.TEKST));
            }
        }
        return new Dialog.Linia(k);
    }

    /** W samouczku do wyboru jest dwóch kandydatów o przeciwnych stylach, poza nim trzech. */
    private <T> List<T> kandydaciStartowi(List<T> wszyscy) {
        return gra.czyTrwaSamouczek() && wszyscy.size() > 2 ? wszyscy.subList(0, 2) : wszyscy;
    }

    private void wybierzMinistraFinansow() {
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(Dialog.Opcja.info(TABELA_FINANSE,
                Wiersze.naglowki("Kandydat", "Styl", "Pensja", "Zniżki", "Cecha", "Wada"), null));
        for (MinisterFinansow kandydat : kandydaciStartowi(gra.getStartowiFinansow())) {
            opcje.add(Dialog.Opcja.wiersz(TABELA_FINANSE, komorkiFinansow(kandydat, null, 0, null),
                    opisMinistra(kandydat), true, () -> wybierzMinistraPracy(kandydat)));
        }
        dialog = Dialog.zLiniami("Wybierz Ministra Finansów · 1/2",
                        List.of(Dialog.Linia.tekst("Pilnuje złota: decyduje, ile wydać na nowe potwory, i wypłaca żołd. "
                                        + "Zniżki obniżają ceny ulepszeń, łapówek i zmian ministrów.", Paleta.TEKST),
                                Dialog.Linia.tekst("Nie ma złego wyboru. Ministra można później wymienić.",
                                        Paleta.TEKST_SLABY)),
                        opcje, this::porzucNowaGre)
                .szerokosc(90).ukrywaj(TABELA_FINANSE, 1);
    }

    private void wybierzMinistraPracy(MinisterFinansow finansow) {
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(Dialog.Opcja.info(TABELA_KADRY,
                Wiersze.naglowki("Kandydat", "Styl", "Pensja", "Werbunek", "Kopalnia", "Cecha", "Wada"), null));
        for (MinisterPracy kandydat : kandydaciStartowi(gra.getStartowiPracy())) {
            opcje.add(Dialog.Opcja.wiersz(TABELA_KADRY, komorkiPracy(kandydat, null, 0, null), opisMinistra(kandydat),
                    true, () -> {
                        gra.rozpocznij(finansow, kandydat);
                        dialog = null;
                        tryb = Tryb.PORANEK;
                        przewiniecie = 0;
                        zapisz();
                        radaPoranna();
                    }));
        }
        dialog = Dialog.zLiniami("Wybierz Ministra Pracy · 2/2",
                        List.of(Dialog.Linia.tekst("Zatrudnia potwory i wysyła co trzeciego do kopalni. Werbunek to "
                                        + "dodatkowi kandydaci co dzień, kopalnia to premia do wydobycia złota.",
                                        Paleta.TEKST),
                                Dialog.Linia.tekst("Tego ministra też można później wymienić.", Paleta.TEKST_SLABY)),
                        opcje, this::porzucNowaGre)
                .szerokosc(96).ukrywaj(TABELA_KADRY, 1);
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
        dialog = Dialog.zLiniami(tytul, akapity(akapity),
                List.of(new Dialog.Opcja("Rozumiem", "", true, this::zamknijDialog)),
                this::zamknijDialog).samoZatwierdzane();
    }

    private void porzucNowaGre() {
        dialog = null;
        gra = null;
        slot = 0;
        czekamNa = null;
        tryb = Tryb.MENU;
    }

    // ---- Claude gra za Władcę ----

    /** Włącza albo wyłącza Claude'a. W trakcie poranka od razu prosi go o decyzje albo przestaje czekać. */
    private void przelaczClaude() {
        claudeGra = !claudeGra;
        if (!claudeGra) {
            czekamNa = null;
            komunikat = null;
        } else if (gra != null && tryb == Tryb.PORANEK) {
            poprosClaudeOPoranek();
        }
    }

    private void poprosClaudeOStart() {
        try {
            czekamNa = most.wyslijStart(gra);
            czekamNaStart = true;
        } catch (IOException | RuntimeException e) {
            bladClaude("Nie udało się zapisać stanu gry dla Claude'a: " + e.getMessage());
            return;
        }
        dialog = new Dialog("Claude wybiera ministrów",
                List.of("Czekam na odpowiedź Claude'a. Stan gry jest w pliku:",
                        most.folder().resolve(MostClaude.PLIK_STANU).toString()),
                List.of(new Dialog.Opcja("Wybierz sam", "Przestań czekać i wybierz ministrów samodzielnie.", true,
                        () -> {
                            czekamNa = null;
                            wybierzMinistraFinansow();
                        })),
                this::porzucNowaGre);
    }

    /** Rano, gdy Claude gra: zapisuje stan i czeka na jego rozkazy. */
    private void poprosClaudeOPoranek() {
        if (!claudeGra || gra == null || gra.czyKoniecGry() || tryb != Tryb.PORANEK
                || gra.getDzien().getFaza() != DzienGry.Faza.PORANEK) {
            return;
        }
        try {
            czekamNa = most.wyslijPoranek(gra);
            czekamNaStart = false;
        } catch (IOException | RuntimeException e) {
            bladClaude("Nie udało się zapisać stanu gry dla Claude'a: " + e.getMessage());
        }
    }

    /** Walka toczy się sama: automat walki jest włączony albo gra Claude. */
    private boolean walkaTrwaSama() {
        return tryb == Tryb.WALKA && (auto || claudeGra) && dialog == null;
    }

    /**
     * Jedno „tyknięcie” gry, gdy gracz niczego nie naciska: krok walki w automacie, sprawdzenie odpowiedzi
     * Claude'a i, gdy gra Claude, samoczynne przechodzenie dalej po krótkiej pauzie.
     */
    void tik(long teraz) {
        if (walkaTrwaSama()) {
            krokWalki();
        }
        sprawdzClaude();
        samaGra(teraz);
    }

    /** Pauza, przez którą gracz widzi ekran albo okno, zanim gra sama przejdzie dalej. */
    static final long PAUZA_SAMEJ_GRY_MS = 2500;
    private Object czekaNaPrzejscie;
    private long odKiedyCzeka;

    /**
     * Gdy gra Claude, gra toczy się sama: okna z decyzjami i podsumowaniem zatwierdzają się same, dzień
     * rusza sam, a po walce dzień sam się kończy. Każdy krok czeka chwilę, żeby gracz zdążył go zobaczyć.
     * Gra zatrzymuje się na upadku lochu i na każdym oknie, które wymaga decyzji gracza.
     */
    private void samaGra(long teraz) {
        Object stan = null;
        if (claudeGra && gra != null && czekamNa == null && gra.getDzien() != null) {
            if (dialog != null) {
                stan = dialog.czySamoZatwierdzane() ? dialog : null;
            } else if (tryb == Tryb.PORANEK || tryb == Tryb.WIECZOR) {
                stan = tryb;
            } else if (tryb == Tryb.WALKA) {
                Walka walka = gra.getDzien().getWalka();
                stan = walka == null || walka.czyZakonczona() ? walka : null;
            }
        }
        if (stan == null) {
            czekaNaPrzejscie = null;
            return;
        }
        if (stan != czekaNaPrzejscie) {
            czekaNaPrzejscie = stan;
            odKiedyCzeka = teraz;
            return;
        }
        if (teraz - odKiedyCzeka < PAUZA_SAMEJ_GRY_MS) {
            return;
        }
        czekaNaPrzejscie = null;
        if (dialog != null) {
            dialog.zatwierdz();
        } else if (tryb == Tryb.PORANEK) {
            rozegrajDzien();
        } else if (tryb == Tryb.WALKA) {
            zakonczDzien();
        } else if (tryb == Tryb.WIECZOR) {
            nastepnyDzien();
        }
    }

    /** Sprawdza, czy Claude już odpowiedział. Wywoływane, gdy gracz niczego nie naciska. */
    void sprawdzClaude() {
        if (czekamNa == null) {
            return;
        }
        try {
            if (czekamNaStart) {
                var start = most.odbierzStart(czekamNa, gra);
                if (start.isPresent()) {
                    czekamNa = null;
                    dialog = null;
                    gra.rozpocznij(start.get().finansow(), start.get().pracy());
                    gra.zanotuj("Claude wybiera ministrów: " + start.get().finansow() + " i " + start.get().pracy()
                            + ". " + jednaLinia(start.get().powod()));
                    most.zapiszHistorie("Start: " + start.get().finansow() + ", " + start.get().pracy());
                    tryb = Tryb.PORANEK;
                    przewiniecie = 0;
                    zapisz();
                    poprosClaudeOPoranek();
                }
            } else {
                var decyzja = most.odbierzPoranek(czekamNa);
                if (decyzja.isPresent()) {
                    czekamNa = null;
                    wykonajDecyzje(decyzja.get());
                }
            }
        } catch (RuntimeException e) {
            czekamNa = null;
            bladClaude("Odpowiedź Claude'a jest błędna: " + e.getMessage());
        }
    }

    /** Wykonuje rozkazy Claude'a po kolei i pokazuje graczowi, co zrobił i dlaczego. */
    private void wykonajDecyzje(MostClaude.Decyzja decyzja) {
        // Numery potworów i ministrów odnoszą się do porannej listy, więc najpierw zamieniamy wszystkie na rozkazy.
        List<Object> rozkazy = new ArrayList<>();
        for (String polecenie : decyzja.rozkazy()) {
            try {
                rozkazy.add(MostClaude.rozkaz(polecenie, gra));
            } catch (RuntimeException e) {
                rozkazy.add("nie rozumiem polecenia: " + polecenie);
            }
        }
        List<Dialog.Wiersz> wiersze = new ArrayList<>();
        StringBuilder wynik = new StringBuilder();
        if (rozkazy.isEmpty()) {
            wiersze.add(new Dialog.Wiersz("Bez rozkazów: dzień idzie tak, jak jest.", Paleta.TEKST));
            wynik.append("bez rozkazów\n");
        }
        for (Object pozycja : rozkazy) {
            String tekst;
            int kolor;
            if (pozycja instanceof Rozkaz rozkaz) {
                String powod = gra.powodOdmowy(rozkaz);
                if (powod == null) {
                    int koszt = gra.koszt(rozkaz);
                    gra.wykonajRozkaz(rozkaz);
                    tekst = "Wykonano: " + rozkaz.nazwa() + (koszt > 0 ? " (koszt " + koszt + ")" : "");
                    kolor = Paleta.ZIELEN;
                } else {
                    tekst = "Odrzucono: " + rozkaz.nazwa() + " (" + powod.toLowerCase() + ")";
                    kolor = Paleta.CZERWIEN;
                }
            } else {
                tekst = "Odrzucono: " + pozycja;
                kolor = Paleta.CZERWIEN;
            }
            wiersze.add(new Dialog.Wiersz(tekst, kolor));
            wynik.append(tekst).append('\n');
        }
        if (!decyzja.powod().isBlank()) {
            wiersze.add(new Dialog.Wiersz("", Paleta.TEKST));
            wiersze.add(new Dialog.Wiersz("Dlaczego: " + jednaLinia(decyzja.powod()), Paleta.TEKST_SLABY));
            gra.zanotuj("Claude: " + jednaLinia(decyzja.powod()));
        }
        most.zapiszHistorie(wynik.toString());
        zapisz();
        dialog = new Dialog(wiersze, "Decyzje Claude'a · dzień " + gra.getNumerDnia(),
                List.of(new Dialog.Opcja("Rozumiem", "Zamknij i rozpocznij dzień.", true,
                        this::zamknijDialog)),
                this::zamknijDialog).samoZatwierdzane();
    }

    private static String jednaLinia(String tekst) {
        return tekst.strip().replaceAll("\\s+", " ");
    }

    private void bladClaude(String opis) {
        dialog = new Dialog("Claude", List.of(opis),
                List.of(new Dialog.Opcja("Poproś jeszcze raz", "Zapisz stan od nowa i czekaj na nową odpowiedź.", true,
                                () -> {
                                    dialog = null;
                                    if (gra != null && gra.getDzien() == null) {
                                        poprosClaudeOStart();
                                    } else {
                                        poprosClaudeOPoranek();
                                    }
                                }),
                        new Dialog.Opcja("Graj sam", "Wyłącz Claude'a.", true, () -> {
                            dialog = null;
                            claudeGra = false;
                            czekamNa = null;
                            if (gra != null && gra.getDzien() == null) {
                                wybierzMinistraFinansow();
                            }
                        })),
                null);
    }

    /** Wczytuje ostatnio zapisaną rozgrywkę w toku. */
    private void kontynuuj() {
        var ostatni = ZapisGry.ostatniDoKontynuacji(ZapisGry.sloty(folderZapisow));
        if (ostatni.isEmpty()) {
            komunikat = "Nie ma rozgrywki do kontynuowania.";
            return;
        }
        wczytajSlot(ostatni.get());
    }

    /**
     * Rozgrywka w toku zapisuje się tylko rano, między rozkazami; dzień w toku odtworzy się z tego samego ziarna.
     * Zakończona rozgrywka zapisuje się w stanie z chwili upadku lochu i zostaje w slocie.
     */
    private void zapisz() {
        if (gra == null || slot == 0 || gra.getDzien() == null) {
            return;
        }
        if (!gra.czyKoniecGry() && gra.getDzien().getFaza() != DzienGry.Faza.PORANEK) {
            return;
        }
        try {
            ZapisGry.zapisz(gra, ZapisGry.plikSlotu(folderZapisow, slot));
        } catch (IOException | RuntimeException e) {
            komunikat = "Nie udało się zapisać gry: " + e.getMessage();
        }
    }

    /** Zapis jest zawsze poranny. Wieczorem najpierw zaczynamy kolejny dzień, żeby nic nie przepadło. */
    private void zapiszPrzyWyjsciu() {
        if (gra != null && tryb == Tryb.WIECZOR && !gra.czyKoniecGry()) {
            gra.rozpocznijDzien();
            tryb = Tryb.PORANEK;
        }
        zapisz();
    }

    private void wyjdzDoMenu() {
        zapiszPrzyWyjsciu();
        dialog = null;
        gra = null;
        slot = 0;
        czekamNa = null;
        tryb = Tryb.MENU;
    }

    private void wyjdzZGry() {
        zapiszPrzyWyjsciu();
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
        // Walka toczy się sama (auto), chyba że gracz wyłączył automat klawiszem A; wybór zostaje na kolejne walki.
        tryb = Tryb.WALKA;
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
        przewiniecie = 0;
        if (gra.czyKoniecGry()) {
            tryb = Tryb.KONIEC_GRY;
            zapisz();
            dialogKoniec();
        } else {
            tryb = Tryb.WIECZOR;
            dialogPodsumowanie();
        }
    }

    /** Okno po upadku lochu. Zapis zostaje w slocie, a loch można obejrzeć po zamknięciu okna. */
    private void dialogKoniec() {
        List<Dialog.Linia> wstep = new ArrayList<>();
        wstep.add(Dialog.Linia.z(Wiersze.slaby("Twoje panowanie trwało "),
                Wiersze.g(gra.getNumerDnia() + " dni", Paleta.ZLOTO), Wiersze.slaby(".")));
        wstep.add(Dialog.Linia.pusta());
        if (slot > 0) {
            wstep.add(Dialog.Linia.z(Wiersze.slaby("Zapis zostaje w slocie "), Wiersze.k(String.valueOf(slot), Paleta.TEKST),
                    Wiersze.slaby(". Wrócisz do niego przez Wczytaj.")));
        }
        wstep.add(Dialog.Linia.z(Wiersze.slaby("Ziarno rozgrywki "), Wiersze.k(String.valueOf(gra.getZiarno()),
                Paleta.TEKST)));
        List<Dialog.Opcja> opcje = new ArrayList<>();
        // Ostatni dzień w ramkach: kto poległ, kto odszedł.
        List<Dialog.Ramka> ramki = gra.getDzien() == null ? List.of() : ramkiZalogi(gra.getDzien().getBilans());
        if (!ramki.isEmpty()) {
            opcje.add(Wiersze.sekcja("Ostatni dzień", null));
            opcje.add(Dialog.Opcja.ramki(ramki));
            opcje.add(Wiersze.odstep());
        }
        opcje.add(new Dialog.Opcja("Obejrzyj loch", "", true, this::zamknijDialog));
        opcje.add(new Dialog.Opcja("Nowa gra", "", true, this::nowaGra));
        opcje.add(new Dialog.Opcja("Menu główne", "", true, this::wyjdzDoMenu));
        dialog = Dialog.zLiniami("Loch upadł", wstep, opcje, this::zamknijDialog);
    }

    /** Wiersz podsumowania: szara etykieta, wartość i szczegóły. */
    private static Dialog.Opcja wierszBilansu(String etykieta, List<Dialog.Kawalek> wartosc, String szczegoly) {
        return Dialog.Opcja.info(0, Wiersze.wiersz(Wiersze.komorka(etykieta, Paleta.TEKST_SLABY), wartosc,
                Wiersze.komorka(szczegoly, Paleta.TEKST_SLABY)), null);
    }

    private static String zeZnakiem(int liczba) {
        return (liczba > 0 ? "+" : liczba < 0 ? "−" : "") + Math.abs(liczba);
    }

    /** Wieczorne podsumowanie: bilans złota, wynik obrony i zmiany w załodze, w równych kolumnach. */
    private void dialogPodsumowanie() {
        BilansDnia bilans = gra.getDzien().getBilans();
        Loch loch = gra.getLoch();
        List<Dialog.Linia> wstep = new ArrayList<>();
        String rada = gra.czyTrwaSamouczek() ? Doradca.wieczor(gra.getNumerDnia()) : null;
        if (rada != null) {
            wstep.add(Dialog.Linia.z(Wiersze.g("Doradca  ", Paleta.ZLOTO), Wiersze.k(rada, Paleta.TEKST)));
        }
        List<Dialog.Opcja> opcje = new ArrayList<>();
        int zmiana = bilans.getZlotoWieczorem() - bilans.getZlotoRano();
        opcje.add(wierszBilansu("Skarbiec", Wiersze.komorka(Wiersze.k(bilans.getZlotoRano() + " → ", Paleta.TEKST),
                        Wiersze.g(String.valueOf(bilans.getZlotoWieczorem()), Paleta.ZLOTO)),
                zeZnakiem(zmiana) + " złota"));
        List<String> przychody = new ArrayList<>();
        pozycja(przychody, "dochód", bilans.getDochod());
        pozycja(przychody, "kopalnia", bilans.getKopalnia());
        pozycja(przychody, "łupy", bilans.getLupy());
        opcje.add(wierszBilansu("Przychody", Wiersze.komorka(Wiersze.g(zeZnakiem(bilans.przychody()), Paleta.ZIELEN)),
                String.join(" · ", przychody)));
        List<String> wydatki = new ArrayList<>();
        pozycja(wydatki, "pensje", bilans.getPensje());
        pozycja(wydatki, "rekrutacja", bilans.getRekrutacja());
        pozycja(wydatki, "rozkazy", bilans.getRozkazy());
        pozycja(wydatki, "łapówka", bilans.getLapowka());
        pozycja(wydatki, "kradzieże", bilans.getSkradzione());
        pozycja(wydatki, "plądrowanie", bilans.getSpladrowane());
        pozycja(wydatki, "żołd", bilans.getZold());
        opcje.add(wierszBilansu("Wydatki", Wiersze.komorka(Wiersze.g(zeZnakiem(-bilans.wydatki()), Paleta.CZERWIEN)),
                String.join(" · ", wydatki)));

        String porazki = "porażki z rzędu: " + loch.getPorazkiZRzedu() + " z " + Loch.PORAZKI_DO_UPADKU;
        List<Dialog.Kawalek> obrona = switch (bilans.getObrona()) {
            case SPOKOJ -> Wiersze.komorka("spokój", Paleta.ZIELEN);
            case PRZEKUPSTWO -> Wiersze.komorka("przekupieni", Paleta.ZLOTO);
            case WYGRANA -> Wiersze.komorka(Wiersze.g("obroniony", Paleta.ZIELEN));
            case PRZEGRANA -> Wiersze.komorka(Wiersze.g("PRZEGRANA", Paleta.CZERWIEN));
            case BEZ_OBRONCOW -> Wiersze.komorka(Wiersze.g("bez obrońców", Paleta.CZERWIEN));
            case NIEROZEGRANA -> Wiersze.komorka("–", Paleta.TEKST_SLABY);
        };
        String szczegolyObrony = switch (bilans.getObrona()) {
            case SPOKOJ -> "nikt nie zaatakował";
            case PRZEKUPSTWO -> "bohaterowie wzięli łapówkę i odeszli";
            case WYGRANA -> "";
            case PRZEGRANA, BEZ_OBRONCOW -> porazki;
            case NIEROZEGRANA -> "";
        };
        opcje.add(wierszBilansu("Obrona", obrona, szczegolyObrony));
        opcje.add(wierszBilansu("Załoga", Wiersze.komorka(loch.getListaPotworow().size() + " / " + Loch.POJEMNOSC,
                Paleta.TEKST), ""));
        if (bilans.getWydobycieNaJutro() > 0) {
            opcje.add(wierszBilansu("Jutro rano", Wiersze.komorka(Wiersze.g("+" + bilans.getWydobycieNaJutro(),
                    Paleta.TURKUS)), "złota z kopalni"));
        }
        List<Dialog.Ramka> ramki = ramkiZalogi(bilans);
        if (!ramki.isEmpty()) {
            opcje.add(Wiersze.odstep());
            opcje.add(Dialog.Opcja.ramki(ramki));
        }
        opcje.add(Wiersze.odstep());
        opcje.add(new Dialog.Opcja("Następny dzień", "", true, () -> {
            dialog = null;
            nastepnyDzien();
        }));
        opcje.add(new Dialog.Opcja("Obejrzyj loch", "", true, this::zamknijDialog));
        dialog = Dialog.zLiniami("Podsumowanie dnia " + gra.getNumerDnia(), wstep, opcje, this::zamknijDialog)
                .samoZatwierdzane();
    }

    private static void pozycja(List<String> pozycje, String nazwa, int kwota) {
        if (kwota > 0) {
            pozycje.add(nazwa + " " + kwota);
        }
    }

    /** Zmiany w załodze: każda grupa we własnej ramce w swoim kolorze, obok siebie. */
    private static List<Dialog.Ramka> ramkiZalogi(BilansDnia bilans) {
        List<Dialog.Ramka> ramki = new ArrayList<>();
        // Odejście ministra pierwsze: zmienia loch na wiele dni, a łatwo je przegapić.
        ramka(ramki, "Odszedł minister", bilans.getOdeszliMinistrowie(), Paleta.CZERWIEN);
        ramka(ramki, "Polegli", bilans.getPolegli(), Paleta.CZERWIEN);
        ramka(ramki, "Odeszli", bilans.getOdeszli(), Paleta.CZERWIEN);
        ramka(ramki, "Bez żołdu", bilans.getNieoplaceni(), Paleta.CZERWIEN);
        ramka(ramki, "Awans", bilans.getAwanse(), Paleta.ZLOTO);
        ramka(ramki, "Zatrudnieni", bilans.getZatrudnieni(), Paleta.ZIELEN);
        return ramki;
    }

    /** Ramka z imionami w podsumowaniu (np. polegli); pusta grupa nie dostaje ramki. */
    private static void ramka(List<Dialog.Ramka> ramki, String nazwa, List<String> imiona, int kolor) {
        if (!imiona.isEmpty()) {
            ramki.add(new Dialog.Ramka(nazwa, kolor, imiona));
        }
    }

    private void nastepnyDzien() {
        gra.rozpocznijDzien();
        tryb = Tryb.PORANEK;
        przewiniecie = 0;
        zapisz();
        radaPoranna();
        poprosClaudeOPoranek();
    }

    // ================= okna rozkazów =================

    private void zamknijDialog() {
        dialog = null;
    }

    private Dialog.Linia pasekRozkazow() {
        return Wiersze.pasekRozkazow(gra.getDzien().getPozostaleRozkazy(), gra.getLoch().getSkarbiec().getZloto());
    }

    /** Legenda strzałek porównania, wspólna dla okien z porównaniem. */
    private static Dialog.Linia legendaPorownania(String zCzym) {
        return Dialog.Linia.z(Wiersze.k("▲", Paleta.ZIELEN), Wiersze.slaby(" lepiej niż " + zCzym + "   "),
                Wiersze.k("▼", Paleta.CZERWIEN), Wiersze.slaby(" gorzej"));
    }

    /** Wiersz tabeli z rozkazem: niedostępny jest przygaszony, a powód trafia do opisu na dole. */
    private Dialog.Opcja wierszRozkazu(int tabela, List<List<Dialog.Kawalek>> komorki, List<Dialog.Linia> opis,
                                       Rozkaz rozkaz) {
        String powod = gra.powodOdmowy(rozkaz);
        List<Dialog.Linia> pelny = new ArrayList<>(opis);
        if (powod != null) {
            pelny.add(Dialog.Linia.tekst("Niedostępne: " + powod.toLowerCase() + ".", Paleta.CZERWIEN));
        } else if (gra.koszt(rozkaz) > 0) {
            // Skutek zamiast rachunku: co się stanie z dzisiejszymi wydatkami po tym zakupie.
            int koszt = gra.koszt(rozkaz);
            int konieczne = gra.brakNaKonieczne(koszt);
            int wszystko = gra.brakPoWydaniu(koszt);
            String lapowka = gra.zaplanowanaLapowka() > 0 ? ", łapówkę" : "";
            pelny.add(konieczne > 0
                    ? Dialog.Linia.tekst("✖ Zabraknie " + konieczne + " złota na pensje ministrów: minister bez "
                            + "pensji odchodzi, a urząd przejmuje stażysta.", Paleta.CZERWIEN)
                    : wszystko > 0
                    ? Dialog.Linia.tekst("! Zabraknie " + wszystko + " złota na żołd" + lapowka + ": części potworów "
                            + "spadnie morale" + (lapowka.isEmpty() ? "" : " albo łapówka nie zostanie zapłacona")
                            + ". Za to mniej złota w skarbcu ściąga słabsze ataki.", Paleta.ZLOTO)
                    : Dialog.Linia.tekst("✔ Starczy na pensje" + lapowka + " i cały żołd.", Paleta.ZIELEN));
        }
        return Dialog.Opcja.wiersz(tabela, komorki, pelny, powod == null, () -> {
            gra.wykonajRozkaz(rozkaz);
            zapisz();
            zamknijDialog();
        });
    }

    /**
     * Koszt rozkazu do kolumny „Koszt”: złoty z ✔, gdy po wydatku starczy na dzisiejsze koszty,
     * czerwony z ✖, gdy zabraknie; kreska, gdy darmowy.
     */
    private List<Dialog.Kawalek> kosztKomorka(int koszt) {
        if (koszt <= 0) {
            return Wiersze.komorka("–", Paleta.TEKST_SLABY);
        }
        boolean konieczne = gra.brakNaKonieczne(koszt) > 0;
        boolean zold = gra.brakPoWydaniu(koszt) > 0;
        int kolor = konieczne ? Paleta.CZERWIEN : zold ? Paleta.ZLOTO : Paleta.ZIELEN;
        return Wiersze.komorka(Wiersze.g(String.valueOf(koszt), konieczne ? Paleta.CZERWIEN : Paleta.ZLOTO),
                Wiersze.k(konieczne ? " ✖" : zold ? " !" : " ✔", kolor));
    }

    // ---- ministrowie ----

    private static final int TABELA_FINANSE = 0;
    private static final int TABELA_KADRY = 1;

    private static String pensjaKrotko(int stala, boolean chciwy) {
        if (stala == 0) {
            return "0";
        }
        double udzial = MinisterFinansow.UDZIAL_W_PRZYCHODZIE + (chciwy ? MinisterFinansow.UDZIAL_CHCIWEGO : 0);
        return stala + "+" + procent(udzial) + "%";
    }

    private static List<Dialog.Kawalek> cechaKomorka(CechaMinistra cecha) {
        return cecha == null ? Wiersze.komorka("–", Paleta.TEKST_SLABY) : Wiersze.komorka("+" + cecha.nazwa(), Paleta.ZIELEN);
    }

    private static List<Dialog.Kawalek> wadaKomorka(WadaMinistra wada) {
        return wada == null ? Wiersze.komorka("–", Paleta.TEKST_SLABY) : Wiersze.komorka("−" + wada.nazwa(), Paleta.CZERWIEN);
    }

    /** Komórki ministra finansów; porównanie z obecnym, gdy jest (null: bez strzałek). */
    private static List<List<Dialog.Kawalek>> komorkiFinansow(MinisterFinansow m, MinisterFinansow obecny, int przychod,
                                                            List<Dialog.Kawalek> koszt) {
        MinisterFinansow wzor = obecny == null ? m : obecny;
        List<List<Dialog.Kawalek>> w = new ArrayList<>(Wiersze.wiersz(
                Wiersze.komorka(Wiersze.g(m.getImie(), Paleta.TEKST)),
                Wiersze.komorka(m.nazwaRodzaju(), Paleta.TEKST),
                Wiersze.porownaj(pensjaKrotko(m.getPensja(), m.ma(WadaMinistra.CHCIWY)), m.pensjaDnia(przychod),
                        wzor.pensjaDnia(przychod), false),
                Wiersze.porownaj(procent(m.getNegocjacje()) + "%", m.getNegocjacje(), wzor.getNegocjacje(), true),
                cechaKomorka(m.getCecha()),
                wadaKomorka(m.getWada())));
        if (koszt != null) {
            w.add(koszt);
        }
        return w;
    }

    private static List<List<Dialog.Kawalek>> komorkiPracy(MinisterPracy m, MinisterPracy obecny, int przychod,
                                                         List<Dialog.Kawalek> koszt) {
        MinisterPracy wzor = obecny == null ? m : obecny;
        List<List<Dialog.Kawalek>> w = new ArrayList<>(Wiersze.wiersz(
                Wiersze.komorka(Wiersze.g(m.getImie(), Paleta.TEKST)),
                Wiersze.komorka(m.nazwaRodzaju(), Paleta.TEKST),
                Wiersze.porownaj(pensjaKrotko(m.getPensja(), m.ma(WadaMinistra.CHCIWY)), m.pensjaDnia(przychod),
                        wzor.pensjaDnia(przychod), false),
                Wiersze.porownaj("+" + m.getWerbunek(), m.getWerbunek(), wzor.getWerbunek(), true),
                Wiersze.porownaj("+" + procent(m.getNadzorKopalni()) + "%", m.getNadzorKopalni(),
                        wzor.getNadzorKopalni(), true),
                cechaKomorka(m.getCecha()),
                wadaKomorka(m.getWada())));
        if (koszt != null) {
            w.add(koszt);
        }
        return w;
    }

    /** Opis kandydata w panelu na dole: styl, cecha, wada i pensja, każde w osobnej linii. */
    private static List<Dialog.Linia> opisMinistra(String imie, String rodzaj, String opisStylu, int pensja,
                                                   boolean chciwy, CechaMinistra cecha, WadaMinistra wada) {
        List<Dialog.Linia> opis = new ArrayList<>();
        opis.add(Dialog.Linia.z(Wiersze.g(imie, Paleta.TEKST), Wiersze.slaby(" · " + rodzaj)));
        opis.add(Wiersze.opis("Styl  ", Paleta.TEKST_SLABY, opisStylu));
        if (cecha != null) {
            opis.add(Wiersze.opis("+ " + cecha.nazwa() + "  ", Paleta.ZIELEN, cecha.opis()));
        }
        if (wada != null) {
            opis.add(Wiersze.opis("− " + wada.nazwa() + "  ", Paleta.CZERWIEN, wada.opis()));
        }
        opis.add(Wiersze.opis("Pensja  ", Paleta.TEKST_SLABY, pensja(pensja, chciwy) + " dziennie."));
        return opis;
    }

    private static List<Dialog.Linia> opisMinistra(MinisterFinansow m) {
        return opisMinistra(m.getImie(), m.nazwaRodzaju(), m.opisStylu(), m.getPensja(), m.ma(WadaMinistra.CHCIWY),
                m.getCecha(), m.getWada());
    }

    private static List<Dialog.Linia> opisMinistra(MinisterPracy m) {
        return opisMinistra(m.getImie(), m.nazwaRodzaju(), m.opisStylu(), m.getPensja(), m.ma(WadaMinistra.CHCIWY),
                m.getCecha(), m.getWada());
    }

    private int przychodDzis() {
        return gra.getDzien() == null ? 0 : gra.getDzien().getBilans().przychody();
    }

    private void dialogMinistrowie() {
        DzienGry dzien = gra.getDzien();
        MinisterFinansow obecnyF = gra.getLoch().getMinisterFinansow();
        MinisterPracy obecnyP = gra.getLoch().getMinisterPracy();
        int przychod = przychodDzis();
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(Wiersze.sekcja("FINANSE", "pilnuje złota, wypłaca żołd i daje zniżki"));
        opcje.add(Dialog.Opcja.info(TABELA_FINANSE,
                Wiersze.naglowki("Kandydat", "Styl", "Pensja", "Zniżki", "Cecha", "Wada", "Koszt"), null));
        opcje.add(Dialog.Opcja.info(TABELA_FINANSE, Wiersze.przygas(komorkiFinansow(obecnyF, null, przychod,
                Wiersze.komorka("–", Paleta.TEKST_SLABY))), "teraz"));
        for (MinisterFinansow k : dzien.getKandydaciFinansow()) {
            Rozkaz r = new Rozkaz.ZmianaMinistraFinansow(k);
            opcje.add(wierszRozkazu(TABELA_FINANSE, komorkiFinansow(k, obecnyF, przychod, kosztKomorka(gra.koszt(r))),
                    opisMinistra(k), r));
        }
        opcje.add(Wiersze.odstep());
        opcje.add(Wiersze.sekcja("KADRY", "zatrudnia potwory i wysyła je do kopalni"));
        opcje.add(Dialog.Opcja.info(TABELA_KADRY,
                Wiersze.naglowki("Kandydat", "Styl", "Pensja", "Werbunek", "Kopalnia", "Cecha", "Wada", "Koszt"), null));
        opcje.add(Dialog.Opcja.info(TABELA_KADRY, Wiersze.przygas(komorkiPracy(obecnyP, null, przychod,
                Wiersze.komorka("–", Paleta.TEKST_SLABY))), "teraz"));
        for (MinisterPracy k : dzien.getKandydaciPracy()) {
            Rozkaz r = new Rozkaz.ZmianaMinistraPracy(k);
            opcje.add(wierszRozkazu(TABELA_KADRY, komorkiPracy(k, obecnyP, przychod, kosztKomorka(gra.koszt(r))),
                    opisMinistra(k), r));
        }
        dialog = Dialog.zLiniami("Zmiana ministra",
                        List.of(pasekRozkazow(), Dialog.Linia.tekst(
                                        "Nowy minister obejmuje urząd od jutra. Kandydaci zmieniają się co dzień.",
                                        Paleta.TEKST_SLABY),
                                legendaPorownania("obecny minister")),
                        opcje, this::zamknijDialog)
                .szerokosc(104).ukrywaj(TABELA_FINANSE, 1).ukrywaj(TABELA_KADRY, 1);
    }

    /** Pensja: stała część plus udział w dziennym przychodzie lochu (większy u chciwego). */
    static String pensja(int stala, boolean chciwy) {
        if (stala == 0) {
            return "0";
        }
        double udzial = MinisterFinansow.UDZIAL_W_PRZYCHODZIE + (chciwy ? MinisterFinansow.UDZIAL_CHCIWEGO : 0);
        return stala + " złota + " + procent(udzial) + "% przychodu";
    }

    /** Cecha i wada ministra jednym zdaniem (puste, gdy minister ich nie ma, np. stażysta). */
    static String cechy(CechaMinistra cecha, WadaMinistra wada) {
        return (cecha == null ? "" : " Cecha: " + cecha.nazwa() + " (" + cecha.opis() + ")")
                + (wada == null ? "" : " Wada: " + wada.nazwa() + " (" + wada.opis() + ")");
    }

    // ---- budżet ----

    private void dialogBudzet() {
        int zloto = gra.getLoch().getSkarbiec().getZloto();
        int obecny = gra.getLoch().getLimitRekrutacji();
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(Dialog.Opcja.info(0, Wiersze.naglowki("Limit", "Na rekrutację dziś", "Kto decyduje"), null));
        for (int procent : new int[] {0, 25, 50, 75, 100}) {
            String kwota = procent == 0 ? "nic" : "do " + (zloto * procent / 100) + " złota";
            String kto = procent == 0 ? "rekrutacja wstrzymana"
                    : procent == 100 ? "Minister Finansów, bez limitu" : "Minister Finansów, w limicie";
            String opis = procent == 0 ? "Wstrzymaj rekrutację: cały skarbiec zostaje na żołd i ulepszenia."
                    : procent == 100 ? "Bez ograniczeń: o budżecie decyduje sam Minister Finansów."
                    : "Minister Finansów odda na rekrutację najwyżej " + procent + "% skarbca.";
            List<Dialog.Kawalek> limit = procent == obecny
                    ? Wiersze.komorka(Wiersze.g(procent + "%", Paleta.TEKST), Wiersze.slaby(" teraz"))
                    : Wiersze.komorka(Wiersze.g(procent + "%", Paleta.TEKST));
            opcje.add(wierszRozkazu(0, Wiersze.wiersz(limit, Wiersze.komorka(kwota, Paleta.ZLOTO),
                    Wiersze.komorka(kto, Paleta.TEKST)), List.of(Dialog.Linia.tekst(opis, Paleta.TEKST)),
                    new Rozkaz.LimitRekrutacji(procent)));
        }
        dialog = Dialog.zLiniami("Budżet rekrutacji", List.of(pasekRozkazow()), opcje, this::zamknijDialog);
    }

    // ---- postawa ----

    private static String postawaKrotko(Postawa postawa) {
        return switch (postawa) {
            case PELNA_OBRONA -> "walczą wszyscy, którzy nie kopią";
            case ODWOD -> "1/3 najlepszych czeka w zasadzce";
            case PRZEKUPSTWO -> "płacisz, bohaterowie odchodzą";
        };
    }

    /** Ocena ataku słowami i kolorem, żeby nie trzeba było liczyć siły lochu. */
    private Dialog.Linia liniaOceny() {
        OcenaAtaku ocena = gra.ocenaAtaku();
        int kolor = switch (ocena) {
            case SPOKOJ, SLABSZY -> Paleta.ZIELEN;
            case WYROWNANY -> Paleta.ZLOTO;
            case GROZNY -> Paleta.CZERWIEN;
        };
        return Dialog.Linia.z(Wiersze.slaby("Atak   "), Wiersze.g(ocena.nazwa(), kolor),
                Wiersze.slaby("  ·  " + ocena.opis() + " (ocena może się mylić)"));
    }

    /** Raport zwiadu w jednej linii: liczba bohaterów, pewny rodzaj i cena łapówki. */
    private Dialog.Linia liniaZwiadu() {
        RaportZwiadu raport = gra.getDzien().getRaport();
        if (raport.spokoj()) {
            return Dialog.Linia.z(Wiersze.slaby("Zwiad  "), Wiersze.k("okolica spokojna, nikt się nie zbliża",
                    Paleta.ZIELEN));
        }
        String liczba = raport.minBohaterow() == raport.maxBohaterow() ? String.valueOf(raport.minBohaterow())
                : raport.minBohaterow() + "–" + raport.maxBohaterow();
        List<Dialog.Kawalek> k = new ArrayList<>(List.of(Wiersze.slaby("Zwiad  "),
                Wiersze.g(liczba + " bohaterów", Paleta.CZERWIEN)));
        if (raport.ujawnionyRodzaj() != null) {
            k.add(Wiersze.slaby("  ·  na pewno "));
            k.add(Wiersze.k(raport.ujawnionyRodzaj(), Paleta.TEKST));
        }
        if (raport.szacowanaSila() > 0) {
            k.add(Wiersze.slaby("  ·  łapówka ok. "));
            k.add(Wiersze.g(raport.szacowanaSila() + " złota", Paleta.ZLOTO));
        }
        return new Dialog.Linia(k);
    }

    private void dialogPostawa() {
        Postawa obecna = gra.getLoch().getPostawa();
        int lapowka = gra.getDzien().getRaport().szacowanaSila();
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(Dialog.Opcja.info(0, Wiersze.naglowki("Postawa", "Na skrót", "Koszt"), null));
        for (Postawa postawa : Postawa.values()) {
            List<Dialog.Kawalek> nazwa = postawa == obecna
                    ? Wiersze.komorka(Wiersze.g(postawa.nazwa(), Paleta.TEKST), Wiersze.slaby(" teraz"))
                    : Wiersze.komorka(Wiersze.g(postawa.nazwa(), Paleta.TEKST));
            List<Dialog.Kawalek> koszt = postawa == Postawa.PRZEKUPSTWO && lapowka > 0
                    ? Wiersze.komorka(Wiersze.g("ok. " + lapowka, Paleta.ZLOTO))
                    : Wiersze.komorka("–", Paleta.TEKST_SLABY);
            opcje.add(wierszRozkazu(0, Wiersze.wiersz(nazwa, Wiersze.komorka(postawaKrotko(postawa), Paleta.TEKST), koszt),
                    List.of(Dialog.Linia.tekst(postawa.opis(), Paleta.TEKST)), new Rozkaz.ZmianaPostawy(postawa)));
        }
        dialog = Dialog.zLiniami("Postawa wobec ataku", List.of(pasekRozkazow(), liniaZwiadu(), liniaOceny()), opcje,
                this::zamknijDialog);
    }

    // ---- ulepszenia ----

    /** Efekt ulepszenia na danym poziomie, np. „+40%”. */
    private static String efekt(Ulepszenie u, int poziom) {
        if (poziom == 0) {
            return "0%";
        }
        return switch (u) {
            case KOPALNIA -> "+" + procent(Ulepszenie.KOPALNIA_NA_POZIOM * poziom) + "%";
            case PULAPKI -> "−" + procent(Ulepszenie.PULAPKI_NA_POZIOM * poziom) + "%";
            case ZAMEK_SKARBCA -> "−" + procent(Ulepszenie.ZAMEK_NA_POZIOM * poziom) + "%";
        };
    }

    private static String coZmienia(Ulepszenie u) {
        return switch (u) {
            case KOPALNIA -> "wydobycie";
            case PULAPKI -> "życie bohaterów";
            case ZAMEK_SKARBCA -> "kradzieże";
        };
    }

    private void dialogUlepszenia() {
        Loch loch = gra.getLoch();
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(Dialog.Opcja.info(0, Wiersze.naglowki("Ulepszenie", "Poziom", "Co zmienia", "Teraz → po zakupie",
                "Koszt"), null));
        for (Ulepszenie u : Ulepszenie.values()) {
            int poziom = loch.poziom(u);
            Rozkaz r = new Rozkaz.ZakupUlepszenia(u);
            List<Dialog.Kawalek> zmiana = poziom >= Ulepszenie.MAX_POZIOM
                    ? Wiersze.komorka(efekt(u, poziom) + " (najwyższy)", Paleta.TEKST)
                    : Wiersze.komorka(Wiersze.k(efekt(u, poziom), Paleta.TEKST), Wiersze.slaby(" → "),
                            Wiersze.g(efekt(u, poziom + 1), Paleta.ZIELEN));
            opcje.add(wierszRozkazu(0, Wiersze.wiersz(Wiersze.komorka(Wiersze.g(u.nazwa(), Paleta.TEKST)),
                    Wiersze.poziom(poziom, Ulepszenie.MAX_POZIOM), Wiersze.komorka(coZmienia(u), Paleta.TEKST_SLABY),
                    zmiana, poziom >= Ulepszenie.MAX_POZIOM ? Wiersze.komorka("–", Paleta.TEKST_SLABY)
                            : kosztKomorka(gra.koszt(r))),
                    List.of(Dialog.Linia.tekst(u.opis(), Paleta.TEKST)), r));
        }
        dialog = Dialog.zLiniami("Ulepszenia lochu", List.of(pasekRozkazow()), opcje, this::zamknijDialog)
                .szerokosc(84).ukrywaj(0, 2);
    }

    // ---- potwory ----

    private static List<Dialog.Kawalek> stanPotwora(Potwor p) {
        if (p.czyRanny()) {
            return Wiersze.komorka("ranny, " + p.getDniRekonwalescencji() + " dni", Paleta.CZERWIEN);
        }
        return p.czyWKopalni() ? Wiersze.komorka("w kopalni", Paleta.TURKUS) : Wiersze.komorka("", Paleta.TEKST);
    }

    /** Moc potwora w walce: ta sama miara, którą gra wycenia obronę lochu (siła, życie i rodzaj). */
    static int moc(Potwor p) {
        return DzienGry.wartoscOddzialu(List.of(p));
    }

    /** Potwory od najsilniejszego. Numery P1.. dla Claude'a nadal idą według kolejności w lochu. */
    static List<Potwor> wedlugMocy(List<Potwor> potwory) {
        List<Potwor> posortowane = new ArrayList<>(potwory);
        posortowane.sort(Comparator.comparingInt(Aplikacja::moc).reversed());
        return posortowane;
    }

    private void dialogPotwory() {
        List<Dialog.Opcja> opcje = new ArrayList<>();
        List<Potwor> potwory = wedlugMocy(gra.getLoch().getListaPotworow());
        if (potwory.isEmpty()) {
            opcje.add(new Dialog.Opcja("Loch jest pusty", "Nie masz żadnego potwora.", false, () -> {
            }));
        } else {
            opcje.add(Dialog.Opcja.info(0, Wiersze.naglowki("Potwór", "Rodzaj", "Poziom", "Moc", "Siła", "Życie",
                    "Morale", "Żołd", ""), null));
        }
        for (Potwor p : potwory) {
            opcje.add(Dialog.Opcja.wiersz(0, Wiersze.wiersz(
                    Wiersze.komorka(Wiersze.k(Sprite.emoji(p) + " ", Paleta.TEKST), Wiersze.g(p.getImie(), Paleta.TEKST)),
                    Wiersze.komorka(p.nazwaRodzaju(), Paleta.TEKST_SLABY),
                    Wiersze.komorka(Wiersze.k("★" + p.getPoziom(), Paleta.ZLOTO)),
                    Wiersze.komorka(Wiersze.g(String.valueOf(moc(p)), Paleta.TEKST)),
                    Wiersze.komorka(String.valueOf(p.getSilaAtaku()), Paleta.TEKST),
                    Wiersze.komorka(String.valueOf(p.getMaxPunktyZycia()), Paleta.TEKST),
                    Wiersze.komorka(p.getMorale() + "/" + p.getMaxMorale(),
                            Wiersze.kolorUdzialu(p.getMorale(), p.getMaxMorale())),
                    Wiersze.komorka(String.valueOf(p.getZold()), Paleta.ZLOTO),
                    stanPotwora(p)), List.of(), true, () -> dialogPotwor(p)));
        }
        dialog = Dialog.zLiniami("Potwory w lochu",
                List.of(pasekRozkazow(), Dialog.Linia.tekst("Wybierz potwora, żeby dać mu premię albo go zwolnić.",
                        Paleta.TEKST_SLABY)),
                opcje, this::zamknijDialog).ukrywaj(0, 1, 8);
    }

    private void dialogPotwor(Potwor potwor) {
        int premia = (int) Math.round(potwor.getMaxMorale() * 0.30);
        int poPremii = Math.min(potwor.getMaxMorale(), potwor.getMorale() + premia);
        Rozkaz rPremia = new Rozkaz.Premia(potwor);
        Rozkaz rZwolnij = new Rozkaz.Zwolnienie(potwor);
        List<Dialog.Opcja> opcje = new ArrayList<>();
        opcje.add(Dialog.Opcja.info(0, Wiersze.naglowki("Rozkaz", "Skutek", "Koszt"), null));
        opcje.add(wierszRozkazu(0, Wiersze.wiersz(Wiersze.komorka(Wiersze.g("Premia", Paleta.TEKST)),
                        Wiersze.komorka(Wiersze.slaby("morale "), Wiersze.k(String.valueOf(potwor.getMorale()), Paleta.TEKST),
                                Wiersze.slaby(" → "), Wiersze.g(poPremii + "/" + potwor.getMaxMorale(), Paleta.ZIELEN)),
                        kosztKomorka(gra.koszt(rPremia))),
                List.of(Dialog.Linia.tekst("Morale rośnie o 30% wartości maksymalnej. Potwór z niskim morale "
                        + "może uciec z walki albo odejść z lochu.", Paleta.TEKST)), rPremia));
        opcje.add(wierszRozkazu(0, Wiersze.wiersz(Wiersze.komorka(Wiersze.g("Zwolnij", Paleta.TEKST)),
                        potwor.czyRanny()
                                ? Wiersze.komorka("odchodzi; reszta załogi traci morale", Paleta.CZERWIEN)
                                : Wiersze.komorka(Wiersze.slaby("odchodzi, żołd "),
                                        Wiersze.k("−" + potwor.getZold() + " dziennie", Paleta.ZIELEN)),
                        kosztKomorka(gra.koszt(rZwolnij))),
                List.of(Dialog.Linia.tekst(potwor.czyRanny()
                        ? "Ranny potwór natychmiast opuszcza loch. Reszta załogi traci przez to trochę morale."
                        : "Potwór natychmiast opuszcza loch i nie pobiera już żołdu.", Paleta.TEKST)), rZwolnij));
        if (potwor.czyRanny()) {
            Rozkaz rSzpital = new Rozkaz.Szpital(potwor);
            opcje.add(wierszRozkazu(0, Wiersze.wiersz(Wiersze.komorka(Wiersze.g("Szpital", Paleta.TEKST)),
                            Wiersze.komorka(Wiersze.slaby("walczy już dziś, zamiast za "),
                                    Wiersze.k(potwor.getDniRekonwalescencji() + " dni", Paleta.ZIELEN)),
                            kosztKomorka(gra.koszt(rSzpital))),
                    List.of(Dialog.Linia.tekst("Ranny wraca do sił od razu i już dziś broni lochu. Kosztuje podwójny "
                            + "żołd za każdy dzień, który i tak by przeleżał (co najmniej " + Rozkaz.Szpital.MIN_KOSZT
                            + " złota). Dzisiejszy atak jest już w drodze, więc szpital go nie osłabi.", Paleta.TEKST)),
                    rSzpital));
        }
        opcje.add(Dialog.Opcja.wiersz(0, Wiersze.wiersz(Wiersze.komorka(Wiersze.g("Wróć", Paleta.TEKST)),
                        Wiersze.komorka("bez rozkazu", Paleta.TEKST_SLABY), Wiersze.komorka("", Paleta.TEKST)),
                List.of(Dialog.Linia.tekst("Wróć do listy potworów.", Paleta.TEKST)), true, this::dialogPotwory));
        List<Dialog.Linia> wstep = new ArrayList<>();
        wstep.add(pasekRozkazow());
        wstep.add(Dialog.Linia.z(Wiersze.k(Sprite.emoji(potwor) + " ", Paleta.TEKST),
                Wiersze.g(potwor.getImie(), Paleta.TEKST), Wiersze.slaby(" · " + potwor.nazwaRodzaju() + " · "),
                Wiersze.k("★" + potwor.getPoziom(), Paleta.ZLOTO)));
        wstep.add(Dialog.Linia.z(Wiersze.slaby("Siła "), Wiersze.g(String.valueOf(potwor.getSilaAtaku()), Paleta.TEKST),
                Wiersze.slaby("   Życie "), Wiersze.g(String.valueOf(potwor.getMaxPunktyZycia()), Paleta.TEKST),
                Wiersze.slaby("   Morale "), Wiersze.g(potwor.getMorale() + "/" + potwor.getMaxMorale(),
                        Wiersze.kolorUdzialu(potwor.getMorale(), potwor.getMaxMorale())),
                Wiersze.slaby("   Żołd "), Wiersze.g(String.valueOf(potwor.getZold()), Paleta.ZLOTO)));
        if (potwor.czyRanny()) {
            wstep.add(Dialog.Linia.tekst("Ranny: wraca do walki za " + potwor.getDniRekonwalescencji() + " dni.",
                    Paleta.CZERWIEN));
        }
        dialog = Dialog.zLiniami(potwor.getImie() + " · " + potwor.nazwaRodzaju(), wstep, opcje, this::dialogPotwory);
    }

    /** Pomoc: w samouczku najpierw bieżąca rada doradcy, potem strony z zasadami. */
    private void dialogPomoc() {
        boolean wGrze = gra != null && tryb != Tryb.MENU;
        List<String> rada = wGrze && gra.czyTrwaSamouczek() && tryb == Tryb.PORANEK
                ? Doradca.poranek(gra.getNumerDnia()) : List.of();
        if (rada.isEmpty()) {
            strona(Doradca.STRONY, 0, "Zamknij", this::zamknijDialog, this::zamknijDialog);
            return;
        }
        dialog = Dialog.zLiniami("Doradca · dzień " + gra.getNumerDnia(), akapity(rada),
                List.of(new Dialog.Opcja("Rozumiem", "Zamknij i graj dalej.", true, this::zamknijDialog),
                        new Dialog.Opcja("Zasady gry", "Cztery krótkie strony o tym, jak działa loch.", true,
                                () -> strona(Doradca.STRONY, 0, "Zamknij", this::zamknijDialog, this::zamknijDialog))),
                this::zamknijDialog);
    }
}
