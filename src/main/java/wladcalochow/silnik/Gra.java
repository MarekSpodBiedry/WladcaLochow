package wladcalochow.silnik;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Loch;
import wladcalochow.model.MinisterFinansow;
import wladcalochow.model.MinisterPracy;
import wladcalochow.model.Potwor;
import wladcalochow.model.Skarbiec;
import wladcalochow.model.Walka;
import wladcalochow.model.Zdarzenie;
import wladcalochow.model.Zdarzenie.Informacja;
import wladcalochow.model.Zdarzenie.Ton;
import wladcalochow.model.strategia.OcenaAtaku;
import wladcalochow.model.strategia.RodzajRozkazu;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.model.strategia.Ulepszenie;

/** Korzeń stanu rozgrywki: loch, bieżący dzień, losowość z ziarnem i dziennik zdarzeń. */
public class Gra implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Wpis dziennika; walka: pojedynczy ruch w walce (stare zapisy bez tego pola wczytują się jako false). */
    public record WpisDziennika(String tekst, Ton ton, boolean walka) implements Serializable {
        public WpisDziennika(String tekst, Ton ton) {
            this(tekst, ton, false);
        }
    }

    public static final int ZLOTO_STARTOWE = 300;
    /** O jaką część maksymalnego morale spada morale każdego potwora, gdy Władca wyrzuca rannego. */
    public static final double SPADEK_MORALE_PO_ZWOLNIENIU_RANNEGO = 0.08;
    private static final int MAX_DZIENNIK = 400;

    private final long ziarno;
    private final boolean samouczek;
    private final Random los;
    private final Loch loch;
    private final List<MinisterFinansow> startowiFinansow;
    private final List<MinisterPracy> startowiPracy;
    private final List<WpisDziennika> dziennik = new ArrayList<>();
    /**
     * Każda decyzja gracza po kolei („dzień 3: POSTAWA ODWOD”), w języku poleceń mostu Claude'a. Razem z ziarnem
     * pozwala odtworzyć całą rozgrywkę. Starsze zapisy nie mają tej listy (null).
     */
    private List<String> decyzje = new ArrayList<>();

    private int numerDnia;
    private DzienGry dzien;
    private MinisterFinansow nastepnyMinisterFinansow;
    private MinisterPracy nastepnyMinisterPracy;
    private boolean koniecGry;

    private Gra(long ziarno, boolean samouczek) {
        this.ziarno = ziarno;
        this.samouczek = samouczek;
        this.los = new Random(ziarno);
        this.loch = new Loch(new Skarbiec(ZLOTO_STARTOWE));
        Set<String> zajete = new HashSet<>();
        loch.zatrudnij(FabrykaIstot.goblin(los, zajete));
        loch.zatrudnij(FabrykaIstot.goblin(los, zajete));
        loch.zatrudnij(FabrykaIstot.ork(los, zajete));
        this.startowiFinansow = GeneratorZdarzen.kandydaciFinansow(los, 3, zajete);
        this.startowiPracy = GeneratorZdarzen.kandydaciPracy(los, 3, zajete);
    }

    /** Nowa rozgrywka. To samo ziarno daje ten sam przebieg przy tych samych decyzjach. */
    public static Gra nowa(long ziarno) {
        return new Gra(ziarno, false);
    }

    /** Nowa rozgrywka z samouczkiem: rozkazy odblokowują się stopniowo, a pierwszy atak jest łatwy. */
    public static Gra nowaZSamouczkiem(long ziarno) {
        return new Gra(ziarno, true);
    }

    /** Pierwsza decyzja Władcy: wybór obu ministrów. Rozpoczyna dzień pierwszy. */
    public void rozpocznij(MinisterFinansow finansow, MinisterPracy pracy) {
        if (numerDnia > 0) {
            throw new IllegalStateException("Gra już trwa");
        }
        loch.setMinisterFinansow(finansow);
        loch.setMinisterPracy(pracy);
        zapiszDecyzje("START MF" + (startowiFinansow.indexOf(finansow) + 1) + " MP" + (startowiPracy.indexOf(pracy) + 1)
                + (samouczek ? " SAMOUCZEK" : ""));
        rozpocznijDzien();
    }

    private void zapiszDecyzje(String polecenie) {
        if (decyzje == null) {
            decyzje = new ArrayList<>();
        }
        decyzje.add("dzień " + numerDnia + ": " + polecenie);
    }

    /** Wszystkie decyzje gracza po kolei (pusta lista dla zapisów sprzed tej funkcji). */
    public List<String> getDecyzje() {
        return decyzje == null ? List.of() : List.copyOf(decyzje);
    }

    /** Rozkaz jako polecenie (to samo, które rozumie most Claude'a); numery liczone od 1, z porannych list. */
    String polecenie(Rozkaz rozkaz) {
        List<Potwor> potwory = loch.getListaPotworow();
        return switch (rozkaz) {
            case Rozkaz.ZmianaMinistraFinansow r -> "MINISTER MF" + (dzien.getKandydaciFinansow().indexOf(r.nowy()) + 1);
            case Rozkaz.ZmianaMinistraPracy r -> "MINISTER MP" + (dzien.getKandydaciPracy().indexOf(r.nowy()) + 1);
            case Rozkaz.LimitRekrutacji r -> "LIMIT " + r.procent();
            case Rozkaz.ZmianaPostawy r -> "POSTAWA " + r.postawa().name();
            case Rozkaz.Premia r -> "PREMIA P" + (potwory.indexOf(r.potwor()) + 1);
            case Rozkaz.Zwolnienie r -> "ZWOLNIJ P" + (potwory.indexOf(r.potwor()) + 1);
            case Rozkaz.Szpital r -> "SZPITAL P" + (potwory.indexOf(r.potwor()) + 1);
            case Rozkaz.ZakupUlepszenia r -> "ULEPSZENIE " + r.ulepszenie().name();
        };
    }

    public void rozpocznijDzien() {
        if (koniecGry) {
            throw new IllegalStateException("Gra jest zakończona");
        }
        numerDnia++;
        loguj(new Informacja("Dzień " + numerDnia, Ton.NAGLOWEK));
        if (nastepnyMinisterFinansow != null) {
            loch.setMinisterFinansow(nastepnyMinisterFinansow);
            loguj(new Informacja("Finanse obejmuje " + nastepnyMinisterFinansow + ".", Ton.NEUTRALNY));
            nastepnyMinisterFinansow = null;
        }
        if (nastepnyMinisterPracy != null) {
            loch.setMinisterPracy(nastepnyMinisterPracy);
            loguj(new Informacja("Kadry obejmuje " + nastepnyMinisterPracy + ".", Ton.NEUTRALNY));
            nastepnyMinisterPracy = null;
        }
        dzien = GeneratorZdarzen.nowyDzien(numerDnia, loch, los, samouczek);
        if (samouczek) {
            dzien.ustawLimitRozkazow(Samouczek.rozkazyNaDzien(numerDnia));
        }
        loguj(dzien.przyjmijPrzychod(loch));
        if (dzien.czyWygadanySkarbiec()) {
            loguj(new Informacja(loch.getMinisterFinansow().getImie() + " wygadał się w karczmie, ile złota jest "
                    + "w skarbcu. Dziś przyjdzie więcej bohaterów.", Ton.ALARM));
        }
        OcenaAtaku ocena = ocenaAtaku();
        loguj(new Informacja(dzien.getRaport().opis()
                + (ocena == OcenaAtaku.SPOKOJ ? "" : " Ocena: " + ocena.nazwa() + "."),
                dzien.getRaport().minBohaterow() > 0 ? Ton.ZLY : Ton.NEUTRALNY));
        if (dzien.getKandydaci().isEmpty()) {
            loguj(new Informacja("Kandydaci do pracy: brak.", Ton.NEUTRALNY));
        } else {
            String lista = dzien.getKandydaci().stream()
                    .map(p -> p.getImie() + " (" + p.nazwaRodzaju() + ", żołd " + p.getZold() + ")")
                    .collect(Collectors.joining(", "));
            loguj(new Informacja("Kandydaci do pracy: " + lista + ".", Ton.NEUTRALNY));
        }
    }

    // ---- rozkazy Władcy ----

    public int koszt(Rozkaz rozkaz) {
        MinisterFinansow finansow = loch.getMinisterFinansow();
        return switch (rozkaz) {
            case Rozkaz.ZmianaMinistraFinansow r ->
                    finansow.cenaPoNegocjacjach(Math.max(30, 3 * r.nowy().getPensja()));
            case Rozkaz.ZmianaMinistraPracy r ->
                    finansow.cenaPoNegocjacjach(Math.max(30, 3 * r.nowy().getPensja()));
            case Rozkaz.Premia r -> Math.max(10, r.potwor().getZold());
            // Podwójny żołd za każdy dzień, który ranny i tak by przeleżał (przy pojedynczym szpital był za mocny).
            case Rozkaz.Szpital r -> Math.max(Rozkaz.Szpital.MIN_KOSZT,
                    2 * r.potwor().getZold() * r.potwor().getDniRekonwalescencji());
            case Rozkaz.ZakupUlepszenia r -> loch.poziom(r.ulepszenie()) >= Ulepszenie.MAX_POZIOM ? 0
                    : finansow.cenaPoNegocjacjach(r.ulepszenie().koszt(loch.poziom(r.ulepszenie()) + 1));
            case Rozkaz.LimitRekrutacji r -> 0;
            case Rozkaz.ZmianaPostawy r -> 0;
            case Rozkaz.Zwolnienie r -> 0;
        };
    }

    /** Zwraca powód, dla którego rozkazu nie da się wydać, albo null, gdy wszystko w porządku. */
    public String powodOdmowy(Rozkaz rozkaz) {
        if (!czyOdblokowany(rozkaz.rodzaj())) {
            return "Dostępne od dnia " + rozkaz.rodzaj().dzienWSamouczku();
        }
        if (koniecGry || dzien == null || !dzien.maRozkazy()) {
            return "Brak rozkazów na dziś";
        }
        String powod = switch (rozkaz) {
            case Rozkaz.ZmianaMinistraFinansow r ->
                    r.nowy() == loch.getMinisterFinansow() ? "Ten minister już urzęduje" : null;
            case Rozkaz.ZmianaMinistraPracy r ->
                    r.nowy() == loch.getMinisterPracy() ? "Ten minister już urzęduje" : null;
            case Rozkaz.LimitRekrutacji r ->
                    r.procent() == loch.getLimitRekrutacji() ? "Taki limit już obowiązuje" : null;
            case Rozkaz.ZmianaPostawy r ->
                    r.postawa() == loch.getPostawa() ? "Taka postawa już obowiązuje" : null;
            case Rozkaz.Premia r -> !loch.getListaPotworow().contains(r.potwor()) ? "Nie ma takiego potwora"
                    : r.potwor().getMorale() >= r.potwor().getMaxMorale() ? "Morale jest już pełne" : null;
            case Rozkaz.Zwolnienie r ->
                    !loch.getListaPotworow().contains(r.potwor()) ? "Nie ma takiego potwora" : null;
            case Rozkaz.Szpital r -> !loch.getListaPotworow().contains(r.potwor()) ? "Nie ma takiego potwora"
                    : !r.potwor().czyRanny() ? "Ten potwór nie jest ranny" : null;
            case Rozkaz.ZakupUlepszenia r ->
                    loch.poziom(r.ulepszenie()) >= Ulepszenie.MAX_POZIOM ? "Najwyższy poziom" : null;
        };
        if (powod == null && koszt(rozkaz) > loch.getSkarbiec().getZloto()) {
            powod = "Za mało złota (potrzeba " + koszt(rozkaz) + ")";
        }
        return powod;
    }

    public Zdarzenie wykonajRozkaz(Rozkaz rozkaz) {
        String powod = powodOdmowy(rozkaz);
        if (powod != null) {
            throw new IllegalStateException(powod);
        }
        int koszt = koszt(rozkaz);
        // Polecenie zapisujemy przed wykonaniem: zwolnienie zmienia numery potworów.
        zapiszDecyzje(polecenie(rozkaz));
        loch.getSkarbiec().wyplac(koszt);
        dzien.zuzyjRozkaz(koszt);
        String skutek = switch (rozkaz) {
            case Rozkaz.ZmianaMinistraFinansow r -> {
                nastepnyMinisterFinansow = r.nowy();
                yield "od jutra finansami rządzi " + r.nowy();
            }
            case Rozkaz.ZmianaMinistraPracy r -> {
                nastepnyMinisterPracy = r.nowy();
                yield "od jutra kadrami rządzi " + r.nowy();
            }
            case Rozkaz.LimitRekrutacji r -> {
                loch.setLimitRekrutacji(r.procent());
                yield "na rekrutację wolno wydać najwyżej " + r.procent() + "% skarbca";
            }
            case Rozkaz.ZmianaPostawy r -> {
                loch.setPostawa(r.postawa());
                yield "postawa lochu: " + r.postawa().nazwa();
            }
            case Rozkaz.Premia r -> {
                r.potwor().premia();
                yield r.potwor().getImie() + " dostaje premię, morale rośnie";
            }
            case Rozkaz.Zwolnienie r -> {
                loch.usun(r.potwor());
                if (r.potwor().czyRanny()) {
                    // Wyrzucenie rannego towarzysza podkopuje zaufanie całej załogi.
                    for (Potwor potwor : loch.getListaPotworow()) {
                        potwor.zmienMorale(-(int) Math.round(potwor.getMaxMorale() * SPADEK_MORALE_PO_ZWOLNIENIU_RANNEGO));
                    }
                    yield r.potwor().getImie() + " zostaje wyrzucony ranny. Załoga traci zaufanie (morale spada)";
                }
                yield r.potwor().getImie() + " opuszcza loch (zwolnienie)";
            }
            case Rozkaz.Szpital r -> {
                r.potwor().wyleczZRan();
                yield r.potwor().getImie() + " wraca ze szpitala i już dziś stanie do walki";
            }
            case Rozkaz.ZakupUlepszenia r -> {
                loch.ulepsz(r.ulepszenie());
                yield r.ulepszenie().nazwa() + " ma teraz poziom " + loch.poziom(r.ulepszenie());
            }
        };
        String cena = koszt > 0 ? ". Koszt: " + koszt + " złota" : "";
        Zdarzenie zdarzenie = new Informacja("Rozkaz: " + skutek + cena + ".", Ton.NEUTRALNY);
        loguj(zdarzenie);
        return zdarzenie;
    }

    /** W samouczku grupy rozkazów pojawiają się w kolejnych dniach; poza nim wszystkie są dostępne. */
    public boolean czyOdblokowany(RodzajRozkazu rodzaj) {
        return !samouczek || numerDnia >= rodzaj.dzienWSamouczku();
    }

    /** Czy grupa rozkazów została odblokowana właśnie dzisiaj. */
    public boolean czyNowy(RodzajRozkazu rodzaj) {
        return samouczek && numerDnia == rodzaj.dzienWSamouczku();
    }

    /** Samouczek trwa przez pierwsze dni gry rozpoczętej w trybie samouczka. */
    public boolean czyTrwaSamouczek() {
        return samouczek && numerDnia <= Samouczek.OSTATNI_DZIEN;
    }

    // ---- przebieg dnia ----

    /** Dochód, pensje, rekrutacja i przydział do kopalni. */
    public List<Zdarzenie> rozegrajPoranek() {
        List<Zdarzenie> zdarzenia = dzien.rozegrajPoranek(loch, los);
        loguj(zdarzenia);
        return zdarzenia;
    }

    /** Zwraca walkę do rozegrania krok po kroku albo null, gdy do walki nie doszło. */
    public Walka rozpocznijObrone() {
        List<Zdarzenie> zdarzenia = new ArrayList<>();
        Walka walka = dzien.przygotujObrone(loch, los, zdarzenia);
        loguj(zdarzenia);
        return walka;
    }

    public List<Zdarzenie> krokWalki() {
        List<Zdarzenie> zdarzenia = dzien.getWalka().krok();
        loguj(zdarzenia);
        return zdarzenia;
    }

    /** Skutki walki, wydobycie i wypłata żołdu. Po tej metodzie dzień jest zakończony. */
    public List<Zdarzenie> zakonczDzien() {
        List<Zdarzenie> zdarzenia = new ArrayList<>(dzien.zakonczObrone(loch, los));
        zdarzenia.addAll(dzien.rozegrajWieczor(loch, los));
        if (loch.czyZdobyty()) {
            koniecGry = true;
            zdarzenia.add(new Informacja("Trzecia porażka bez wygranej: bohaterowie zdobywają loch. "
                    + "To koniec panowania.", Ton.ALARM));
        } else if (loch.getListaPotworow().isEmpty() && loch.getSkarbiec().czyPusty()) {
            koniecGry = true;
            zdarzenia.add(new Informacja("Loch opustoszał, a skarbiec świeci pustkami. To koniec panowania.",
                    Ton.ALARM));
        } else if (loch.getPorazkiZRzedu() > 0 && dzien.getWalka() != null
                && dzien.getWalka().getWynik() == Walka.Wynik.OBRONA_PRZEGRANA) {
            zdarzenia.add(new Informacja("Porażki bez wygranej: " + loch.getPorazkiZRzedu() + " z "
                    + Loch.PORAZKI_DO_UPADKU + ". Licznik zeruje tylko wygrana obrona. Przy trzeciej loch upadnie.",
                    Ton.ALARM));
        }
        loguj(zdarzenia);
        return zdarzenia;
    }

    /** Dopisuje do dziennika notatkę spoza przebiegu dnia, np. uzasadnienie decyzji Władcy. */
    public void zanotuj(String tekst) {
        loguj(new Informacja(tekst, Ton.NEUTRALNY));
    }

    private void loguj(List<Zdarzenie> zdarzenia) {
        zdarzenia.forEach(this::loguj);
    }

    private void loguj(Zdarzenie zdarzenie) {
        dziennik.add(new WpisDziennika(zdarzenie.opis(), zdarzenie.ton(), zdarzenie.walka()));
        if (dziennik.size() > MAX_DZIENNIK) {
            dziennik.subList(0, dziennik.size() - MAX_DZIENNIK).clear();
        }
    }

    // ---- podpowiedzi zamiast liczenia ----

    /**
     * Zapowiadany atak oceniony słowami: porównanie ceny łapówki ze zwiadu (wartość bohaterów) z obroną lochu
     * (wartość potworów × 2/3, tak jak liczą ją bohaterowie). Gracz nie musi tego liczyć sam.
     */
    public OcenaAtaku ocenaAtaku() {
        if (dzien == null) {
            return OcenaAtaku.SPOKOJ;
        }
        int obrona = DzienGry.wartoscOddzialu(loch.getListaPotworow()) * 2 / 3;
        return OcenaAtaku.ocen(dzien.getRaport().szacowanaSila(), obrona, dzien.getRaport().spokoj());
    }

    /** Dzisiejsze stałe koszty: pensje obu ministrów i żołd całej załogi. */
    public int kosztyDnia() {
        return pensjeDzis() + loch.dziennyKosztZoldu();
    }

    private int pensjeDzis() {
        int przychod = dzien == null ? 0 : dzien.getBilans().przychody();
        return loch.getMinisterFinansow().pensjaDnia(przychod) + loch.getMinisterPracy().pensjaDnia(przychod);
    }

    /** Łapówka, którą trzeba będzie dziś zapłacić, jeśli obowiązuje przekupstwo, a zwiad widzi bohaterów. */
    public int zaplanowanaLapowka() {
        return dzien == null || loch.getPostawa() != wladcalochow.model.strategia.Postawa.PRZEKUPSTWO
                ? 0 : dzien.getRaport().szacowanaSila();
    }

    /**
     * O ile złota zabraknie na pensje ministrów, jeśli teraz wydamy podaną kwotę. Brak tutaj to poważna sprawa:
     * minister bez pensji odchodzi. (Rezerwa na łapówkę i żołd w symulacji szkodziła: wydane złoto mniej
     * przyciąga bohaterów, więc ostrzegamy o nich tylko na żółto.)
     */
    public int brakNaKonieczne(int wydatek) {
        return Math.max(0, pensjeDzis() - (loch.getSkarbiec().getZloto() - wydatek));
    }

    /** O ile złota zabraknie na wszystko dzisiaj (konieczne i żołd), jeśli teraz wydamy podaną kwotę (0: starczy). */
    public int brakPoWydaniu(int wydatek) {
        return Math.max(0, kosztyDnia() + zaplanowanaLapowka() - (loch.getSkarbiec().getZloto() - wydatek));
    }

    public Loch getLoch() {
        return loch;
    }

    public DzienGry getDzien() {
        return dzien;
    }

    public int getNumerDnia() {
        return numerDnia;
    }

    public long getZiarno() {
        return ziarno;
    }

    public boolean czyKoniecGry() {
        return koniecGry;
    }

    public List<WpisDziennika> getDziennik() {
        return Collections.unmodifiableList(dziennik);
    }

    public List<MinisterFinansow> getStartowiFinansow() {
        return List.copyOf(startowiFinansow);
    }

    public List<MinisterPracy> getStartowiPracy() {
        return List.copyOf(startowiPracy);
    }

    public MinisterFinansow getNastepnyMinisterFinansow() {
        return nastepnyMinisterFinansow;
    }

    public MinisterPracy getNastepnyMinisterPracy() {
        return nastepnyMinisterPracy;
    }
}
