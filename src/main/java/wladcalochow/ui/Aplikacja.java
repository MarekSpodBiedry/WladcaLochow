package wladcalochow.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import wladcalochow.model.strategia.Postawa;
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
            while (!koniec) {
                rysuj();
                Wejscie wejscie = terminal.czytaj(walkaTrwaSama() ? 450 : 300);
                switch (wejscie) {
                    case Wejscie.Brak brak -> tik(System.currentTimeMillis());
                    case Wejscie.Koniec k -> {
                        zapiszPrzyWyjsciu();
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

    /** Prostokąty stref klikalnych bieżącej klatki: x, y, szerokość, wysokość. */
    List<int[]> prostokatyStref() {
        return strefy.stream().map(s -> new int[] {s.x(), s.y(), s.w(), s.h()}).toList();
    }

    void obsluz(Wejscie wejscie) {
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
        linia(e, tx, y + 1, tw, Sprite.FINANSE + " Finanse: " + finansow.getImie(), Paleta.TEKST, true);
        linia(e, tx + 3, y + 2, tw - 3, finansow.nazwaRodzaju() + ", pensja " + finansow.getPensja()
                + ", zniżki " + procent(finansow.getNegocjacje()) + "%", Paleta.TEKST_SLABY, false);
        if (gra.getNastepnyMinisterFinansow() != null) {
            linia(e, tx + 3, y + 3, tw - 3, "od jutra: " + gra.getNastepnyMinisterFinansow().getImie(),
                    Paleta.POMARANCZ, false);
        }
        linia(e, tx, y + 4, tw, Sprite.KADRY + " Kadry: " + pracy.getImie(), Paleta.TEKST, true);
        linia(e, tx + 3, y + 5, tw - 3, pracy.nazwaRodzaju() + ", pensja " + pracy.getPensja(),
                Paleta.TEKST_SLABY, false);
        linia(e, tx + 3, y + 6, tw - 3, "werbunek +" + pracy.getWerbunek() + ", kopalnia +"
                + procent(pracy.getNadzorKopalni()) + "%", Paleta.TEKST_SLABY, false);
        if (gra.getNastepnyMinisterPracy() != null) {
            linia(e, tx + 3, y + 7, tw - 3, "od jutra: " + gra.getNastepnyMinisterPracy().getImie(),
                    Paleta.POMARANCZ, false);
        }
        if (loch.getWydobycieDoOdebrania() > 0) {
            linia(e, tx, y + 8, tw, "Z kopalni jutro rano: +" + loch.getWydobycieDoOdebrania() + " złota",
                    Paleta.TURKUS, false);
        }
        linia(e, tx, y + 9, tw, "Limit rekrutacji: " + loch.getLimitRekrutacji() + "% skarbca", Paleta.TEKST,
                false);
        // Poziomy ulepszeń liczbami (pięć kratek na każde ulepszenie nie mieści się w najwęższym oknie).
        int ux = tx;
        for (Ulepszenie ulepszenie : Ulepszenie.values()) {
            ux = e.pisz(ux, y + 10, skrotUlepszenia(ulepszenie) + " ", Paleta.TEKST_SLABY);
            int poziom = loch.poziom(ulepszenie);
            ux = e.pisz(ux, y + 10, poziom + "/" + Ulepszenie.MAX_POZIOM, poziom > 0 ? Paleta.TURKUS : Paleta.TEKST_SLABY);
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

    /** Jeden wiersz panelu. Gdyby tekst się nie zmieścił, dostanie wielokropek, który wyłapują testy układu. */
    private static void linia(Ekran e, int x, int y, int maks, String tekst, int kolor, boolean pogrubiony) {
        e.pisz(x, y, Ekran.przytnij(tekst, maks), kolor, Ekran.BEZ_ZMIAN, pogrubiony);
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
        for (ZapisGry.Slot zajety : sloty) {
            opcje.add(new Dialog.Opcja(opisSlotu(zajety), "Ta rozgrywka zostanie zastąpiona nową.", true,
                    () -> nowaGraWSlocie(zajety.numer())));
        }
        dialog = new Dialog("Nowa gra · wszystkie sloty zajęte",
                List.of("Wszystkie " + ZapisGry.SLOTY + " slotów jest zajętych. Wybierz slot, którego zapis "
                        + "zastąpi nowa gra. Pozostałe zapisy zostaną bez zmian."),
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

    /** Opis slotu w jednym wierszu: numer, dzień, stan i data zapisu. */
    private static String opisSlotu(ZapisGry.Slot s) {
        String naglowek = "Slot " + s.numer() + " · ";
        if (!s.zajety()) {
            return naglowek + "pusty";
        }
        String data = s.czas() == null ? "" : " · " + FORMAT_DATY.format(s.czas().toInstant());
        if (s.gra().isEmpty()) {
            return naglowek + "zapis nie do odczytania" + data;
        }
        Gra g = s.gra().get();
        String stan = g.czyKoniecGry() ? "przegrana w dniu " + g.getNumerDnia()
                : g.getDzien() == null ? "przed pierwszym dniem" : "dzień " + g.getNumerDnia();
        return naglowek + stan + " · złoto " + g.getLoch().getSkarbiec().getZloto() + " · potwory "
                + g.getLoch().getListaPotworow().size() + data;
    }

    /** Lista slotów: rozgrywkę w toku można kontynuować, przegraną obejrzeć. */
    private void dialogWczytaj() {
        komunikat = null;
        List<Dialog.Opcja> opcje = new ArrayList<>();
        for (ZapisGry.Slot s : ZapisGry.sloty(folderZapisow)) {
            String szczegol = !s.zajety() ? ""
                    : s.gra().isEmpty() ? "Pochodzi z innej wersji gry albo jest uszkodzony."
                    : s.doKontynuacji() ? "Graj dalej."
                    : "Rozgrywka zakończona: można obejrzeć jej ostatni dzień.";
            boolean aktywna = s.gra().isPresent() && (s.doKontynuacji() || s.gra().get().czyKoniecGry());
            opcje.add(new Dialog.Opcja(opisSlotu(s), szczegol, aktywna, () -> wczytajSlot(s)));
        }
        dialog = new Dialog("Wczytaj grę",
                List.of("Gra nigdy sama nie usuwa zapisów. Przegrane rozgrywki zostają w swoich slotach."),
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
        List<Dialog.Wiersz> wiersze = new ArrayList<>();
        for (String akapit : strona.akapity()) {
            if (!wiersze.isEmpty()) {
                wiersze.add(new Dialog.Wiersz("", Paleta.TEKST));
            }
            wiersze.add(new Dialog.Wiersz(akapit, Paleta.TEKST));
        }
        dialog = new Dialog(wiersze, strona.tytul() + " · " + (numer + 1) + "/" + strony.size(),
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
                    kandydat.opisStylu() + cechy(kandydat.getCecha(), kandydat.getWada()), true,
                    () -> wybierzMinistraPracy(kandydat)));
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
                    kandydat.opisStylu() + cechy(kandydat.getCecha(), kandydat.getWada()), true, () -> {
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
        List<String> wstep = new ArrayList<>();
        wstep.add("Twoje panowanie trwało " + gra.getNumerDnia() + " dni.");
        if (slot > 0) {
            wstep.add("Zapis tej rozgrywki zostaje w slocie " + slot + ". Możesz do niego wrócić przez Wczytaj.");
        }
        wstep.add("Ziarno tej rozgrywki: " + gra.getZiarno());
        dialog = new Dialog("Loch upadł", wstep,
                List.of(new Dialog.Opcja("Obejrzyj loch", "Zamknij okno i zobacz ostatni dzień.", true,
                                this::zamknijDialog),
                        new Dialog.Opcja("Nowa gra", "Zacznij od nowa z innym losowaniem.", true, this::nowaGra),
                        new Dialog.Opcja("Menu główne", "Wróć do ekranu tytułowego.", true, this::wyjdzDoMenu)),
                this::zamknijDialog);
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
                + ", kopalnia " + bilans.getKopalnia() + (bilans.getLupy() > 0 ? ", łupy " + bilans.getLupy() : ""),
                Paleta.ZIELEN));
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

        boolean zmiany = !bilans.getZatrudnieni().isEmpty() || !bilans.getPolegli().isEmpty()
                || !bilans.getOdeszli().isEmpty() || !bilans.getNieoplaceni().isEmpty()
                || !bilans.getAwanse().isEmpty();
        wiersze.add(new Dialog.Wiersz("Załoga: " + loch.getListaPotworow().size() + " z " + Loch.POJEMNOSC
                + (zmiany ? "" : ", bez zmian"), Paleta.TEKST));
        lista(wiersze, "Zatrudnieni", bilans.getZatrudnieni(), Paleta.ZIELEN);
        lista(wiersze, "Awans", bilans.getAwanse(), Paleta.ZIELEN);
        lista(wiersze, "Polegli", bilans.getPolegli(), Paleta.CZERWIEN);
        lista(wiersze, "Zabrakło żołdu dla", bilans.getNieoplaceni(), Paleta.CZERWIEN);
        lista(wiersze, "Odeszli z lochu", bilans.getOdeszli(), Paleta.CZERWIEN);
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
                this::zamknijDialog).samoZatwierdzane();
    }

    private static void pozycja(List<String> pozycje, String nazwa, int kwota) {
        if (kwota > 0) {
            pozycje.add(nazwa + " " + kwota);
        }
    }

    private static void lista(List<Dialog.Wiersz> wiersze, String nazwa, List<String> imiona, int kolor) {
        if (!imiona.isEmpty()) {
            wiersze.add(new Dialog.Wiersz("  " + nazwa + ": " + String.join(", ", imiona), kolor));
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

    private String wstepRozkazow() {
        return "Pozostałe rozkazy na dziś: " + gra.getDzien().getPozostaleRozkazy() + ". Złoto w skarbcu: "
                + gra.getLoch().getSkarbiec().getZloto() + ".";
    }

    private Dialog.Opcja opcjaRozkazu(String etykieta, String szczegol, Rozkaz rozkaz) {
        String powod = gra.powodOdmowy(rozkaz);
        int koszt = gra.koszt(rozkaz);
        String dopisek = powod != null ? " Niedostępne: " + powod.toLowerCase() + "."
                : koszt > 0 ? " Koszt: " + koszt + " złota." : "";
        return new Dialog.Opcja(etykieta, szczegol + dopisek, powod == null, () -> {
            gra.wykonajRozkaz(rozkaz);
            zapisz();
            zamknijDialog();
        });
    }

    private void dialogMinistrowie() {
        DzienGry dzien = gra.getDzien();
        MinisterFinansow obecnyFinansow = gra.getLoch().getMinisterFinansow();
        MinisterPracy obecnyPracy = gra.getLoch().getMinisterPracy();
        List<Dialog.Opcja> opcje = new ArrayList<>();
        for (MinisterFinansow kandydat : dzien.getKandydaciFinansow()) {
            opcje.add(opcjaRozkazu("Finanse: " + kandydat.getImie() + " · " + kandydat.nazwaRodzaju(),
                    kandydat.opisStylu() + " " + statystyki(kandydat), new Rozkaz.ZmianaMinistraFinansow(kandydat)));
        }
        for (MinisterPracy kandydat : dzien.getKandydaciPracy()) {
            opcje.add(opcjaRozkazu("Kadry: " + kandydat.getImie() + " · " + kandydat.nazwaRodzaju(),
                    kandydat.opisStylu() + " " + statystyki(kandydat), new Rozkaz.ZmianaMinistraPracy(kandydat)));
        }
        dialog = new Dialog("Zmiana ministra",
                List.of(wstepRozkazow(), "Nowy minister obejmuje urząd od jutra. Kandydaci zmieniają się co dzień.",
                        "Teraz finanse: " + obecnyFinansow.getImie() + " · " + obecnyFinansow.nazwaRodzaju() + ". "
                                + statystyki(obecnyFinansow),
                        "Teraz kadry: " + obecnyPracy.getImie() + " · " + obecnyPracy.nazwaRodzaju() + ". "
                                + statystyki(obecnyPracy)),
                opcje, this::zamknijDialog);
    }

    private static String statystyki(MinisterFinansow minister) {
        return "Pensja " + pensja(minister.getPensja(), minister.ma(WadaMinistra.CHCIWY)) + ", zniżki "
                + procent(minister.getNegocjacje()) + "%." + cechy(minister.getCecha(), minister.getWada());
    }

    private static String statystyki(MinisterPracy minister) {
        return "Pensja " + pensja(minister.getPensja(), minister.ma(WadaMinistra.CHCIWY)) + ", werbunek +"
                + minister.getWerbunek() + ", kopalnia +" + procent(minister.getNadzorKopalni()) + "%."
                + cechy(minister.getCecha(), minister.getWada());
    }

    /** Pensja: stała część plus udział w dziennym przychodzie lochu (większy u chciwego). */
    static String pensja(int stala, boolean chciwy) {
        if (stala == 0) {
            return "0";
        }
        double udzial = MinisterFinansow.UDZIAL_W_PRZYCHODZIE + (chciwy ? MinisterFinansow.UDZIAL_CHCIWEGO : 0);
        return stala + " + " + procent(udzial) + "% przychodu";
    }

    /** Cecha i wada ministra jednym zdaniem (puste, gdy minister ich nie ma, np. stażysta). */
    static String cechy(CechaMinistra cecha, WadaMinistra wada) {
        return (cecha == null ? "" : " Cecha: " + cecha.nazwa() + " (" + cecha.opis() + ")")
                + (wada == null ? "" : " Wada: " + wada.nazwa() + " (" + wada.opis() + ")");
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
                            + potwor.getMorale() + "/" + potwor.getMaxMorale() + " · żołd " + potwor.getZold()
                            + (potwor.czyRanny() ? " · ranny, wraca za " + potwor.getDniRekonwalescencji() + " dni" : ""),
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
        opcje.add(opcjaRozkazu("Zwolnij", potwor.czyRanny()
                        ? "Ranny potwór natychmiast opuszcza loch. Reszta załogi traci przez to trochę morale."
                        : "Potwór natychmiast opuszcza loch i nie pobiera już żołdu.",
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
            strona(Doradca.STRONY, 0, "Zamknij", this::zamknijDialog, this::zamknijDialog);
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
                                () -> strona(Doradca.STRONY, 0, "Zamknij", this::zamknijDialog, this::zamknijDialog))),
                this::zamknijDialog);
    }
}
