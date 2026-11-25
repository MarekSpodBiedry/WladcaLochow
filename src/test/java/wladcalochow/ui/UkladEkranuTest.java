package wladcalochow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wladcalochow.model.BylejakiMinisterPracy;
import wladcalochow.model.Loch;
import wladcalochow.model.Ogr;
import wladcalochow.model.ZachowawczyMinisterFinansow;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.silnik.FabrykaIstot;
import wladcalochow.silnik.Gra;

/**
 * Automat klika po całej grze na różnych rozmiarach okna i po każdym kroku sprawdza ekran:
 * żaden tekst nie może być przycięty (wielokropek), wyjść poza ekran ani nachodzić na inny przycisk.
 */
class UkladEkranuTest {

    /** Terminal o stałym rozmiarze, który niczego nie wyświetla. */
    private static final class SztucznaKonsola implements Konsola {
        private final int szer;
        private final int wys;

        SztucznaKonsola(int szer, int wys) {
            this.szer = szer;
            this.wys = wys;
        }

        @Override
        public int szer() {
            return szer;
        }

        @Override
        public int wys() {
            return wys;
        }

        @Override
        public boolean pelnyKolor() {
            return true;
        }

        @Override
        public void wypisz(String tekst) {
        }

        @Override
        public Wejscie czytaj(long milisekundy) {
            return new Wejscie.Brak();
        }

        @Override
        public void close() {
        }
    }

    /** Fragmenty ekranów, które automat musi po drodze zobaczyć, żeby test miał sens. */
    private static final List<String> WYMAGANE_EKRANY = List.of("Nowa gra", "Odwrócona bajka", "Dlaczego warto", "Kim jesteś", "Wybierz Ministra Pracy",
            "Doradca · dzień 2", "Doradca · pierwsza walka", "Podsumowanie dnia", "Ulepszenia lochu",
            "Postawa wobec ataku", "Budżet rekrutacji", "Potwory w lochu", "Zmiana ministra", "Bohaterowie",
            "Loch upadł", "od jutra:", "Z kopalni jutro rano", "Jest nowa wersja 12.34.56", "Nowa wersja 12.34.56");

    private final Set<String> widziane = new HashSet<>();

    @Test
    void najmniejszeOkno(@TempDir Path katalog) {
        grajISprawdzaj(katalog, Aplikacja.MIN_SZER, Aplikacja.MIN_WYS, 12, 1500);
        for (String ekran : WYMAGANE_EKRANY) {
            assertTrue(widziane.contains(ekran), "automat nie dotarł do ekranu: " + ekran);
        }
    }

    /** Najgorszy przypadek: pełny loch, najdłuższe imiona, ogromne kwoty i atak dwunastu bohaterów. */
    @Test
    void skrajneStanyMieszczaSieWNajmniejszymOknie(@TempDir Path katalog) {
        int szer = Aplikacja.MIN_SZER;
        int wys = Aplikacja.MIN_WYS;
        String dlugieImie = "W".repeat(FabrykaIstot.MAX_DLUGOSC_IMIENIA);
        boolean widzianoDwunastu = false;
        for (long ziarno = 1; ziarno <= 6; ziarno++) {
            Gra gra = Gra.nowa(ziarno);
            gra.rozpocznij(new ZachowawczyMinisterFinansow("Zenobiusz Rachmistrz", 32, 0.25),
                    new BylejakiMinisterPracy("Brygadzista Kilof", 37, 2, 0.25));
            Loch loch = gra.getLoch();
            loch.getSkarbiec().wplac(900_000);
            loch.dodajWydobycie(54_321);
            for (int i = 0; loch.wolneMiejsca() > 0; i++) {
                loch.zatrudnij(new Ogr(dlugieImie.substring(1) + (char) ('a' + i), 60, 900, 100, 45));
            }
            gra.wykonajRozkaz(new Rozkaz.ZmianaMinistraFinansow(gra.getDzien().getKandydaciFinansow().get(0)));
            gra.wykonajRozkaz(new Rozkaz.ZmianaMinistraPracy(gra.getDzien().getKandydaciPracy().get(0)));

            Aplikacja aplikacja = new Aplikacja(new SztucznaKonsola(szer, wys),
                    katalog.resolve("skrajny-" + ziarno), ziarno);
            aplikacja.pokazGre(gra);
            aplikacja.rysuj();
            sprawdz(aplikacja, szer, wys, "skrajny stan, ziarno " + ziarno);
            Random bot = new Random(ziarno);
            for (int krok = 0; krok < 900 && !gra.czyKoniecGry(); krok++) {
                loch.getSkarbiec().wplac(50_000);
                widzianoDwunastu |= gra.getDzien().getBohaterowie().size() == 12
                        && aplikacja.klatka().tekst().contains("Bohaterowie");
                Wejscie wejscie = bot.nextInt(10) == 0 ? losoweWejscie(aplikacja, bot)
                        : new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER);
                aplikacja.obsluz(wejscie);
                aplikacja.rysuj();
                sprawdz(aplikacja, szer, wys, "skrajny stan, ziarno " + ziarno + ", krok " + krok);
            }
        }
        assertTrue(widzianoDwunastu, "test miał pokazać walkę z dwunastoma bohaterami");
    }

    @Test
    void srednieOkno(@TempDir Path katalog) {
        grajISprawdzaj(katalog, 124, 36, 6, 1500);
    }

    @Test
    void duzeOkno(@TempDir Path katalog) {
        grajISprawdzaj(katalog, 170, 48, 4, 1500);
    }

    private void grajISprawdzaj(Path katalog, int szer, int wys, int rozgrywki, int kroki) {
        for (int numer = 1; numer <= rozgrywki; numer++) {
            Random bot = new Random(numer * 977L + szer);
            Path zapis = katalog.resolve("zapisy-" + numer);
            Aplikacja aplikacja = new Aplikacja(new SztucznaKonsola(szer, wys), zapis, (long) numer);
            if (numer % 2 == 0) {
                aplikacja.ustawNowaWersje("12.34.56");
            }
            aplikacja.rysuj();
            sprawdz(aplikacja, szer, wys, "start");
            for (int krok = 0; krok < kroki; krok++) {
                if (aplikacja.czyKoniec()) {
                    aplikacja = new Aplikacja(new SztucznaKonsola(szer, wys), zapis, (long) numer + krok);
                    aplikacja.rysuj();
                }
                Wejscie wejscie = losoweWejscie(aplikacja, bot);
                aplikacja.obsluz(wejscie);
                aplikacja.rysuj();
                sprawdz(aplikacja, szer, wys, "rozgrywka " + numer + ", krok " + krok + ", wejście " + wejscie);
            }
        }
    }

    private static Wejscie losoweWejscie(Aplikacja aplikacja, Random bot) {
        int rzut = bot.nextInt(100);
        if (aplikacja.czyDialog()) {
            if (rzut < 45) {
                return new Wejscie.Znak((char) ('1' + bot.nextInt(4)));
            }
            if (rzut < 75) {
                return new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER);
            }
            if (rzut < 85) {
                return new Wejscie.Klawisz(Wejscie.Rodzaj.DOL);
            }
            if (rzut < 90) {
                return new Wejscie.Klawisz(Wejscie.Rodzaj.GORA);
            }
            return new Wejscie.Klawisz(Wejscie.Rodzaj.ESC);
        }
        List<Character> skroty = aplikacja.dostepneSkroty();
        skroty.remove(Character.valueOf('q'));
        if (rzut < 45 || skroty.isEmpty()) {
            return new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER);
        }
        if (rzut < 50) {
            return new Wejscie.Klawisz(bot.nextBoolean() ? Wejscie.Rodzaj.GORA : Wejscie.Rodzaj.DOL);
        }
        if (rzut < 52) {
            return new Wejscie.Znak('q');
        }
        return new Wejscie.Znak(skroty.get(bot.nextInt(skroty.size())));
    }

    private void sprawdz(Aplikacja aplikacja, int szer, int wys, String gdzie) {
        Ekran klatka = aplikacja.klatka();
        String tekst = klatka.tekst();
        String opis = gdzie + " (okno " + szer + "x" + wys + ")\n" + tekst;
        for (String ekran : WYMAGANE_EKRANY) {
            if (tekst.contains(ekran)) {
                widziane.add(ekran);
            }
        }
        // „… i jeszcze N” to celowy skrót listy istot (np. przy włączonych radach eksperta), a nie przycięty tekst.
        assertFalse(tekst.replace("… i jeszcze", "").contains("…"), "przycięty tekst: " + opis);
        assertEquals(0, klatka.pozaEkranem(), "tekst poza ekranem: " + opis);
        List<int[]> strefy = aplikacja.prostokatyStref();
        for (int i = 0; i < strefy.size(); i++) {
            int[] a = strefy.get(i);
            if (a[0] < 0 || a[1] < 0 || a[0] + a[2] > szer || a[1] + a[3] > wys) {
                fail("strefa klikalna poza ekranem: " + opis);
            }
            for (int j = i + 1; j < strefy.size(); j++) {
                int[] b = strefy.get(j);
                boolean rozlaczne = a[0] + a[2] <= b[0] || b[0] + b[2] <= a[0]
                        || a[1] + a[3] <= b[1] || b[1] + b[3] <= a[1];
                if (!rozlaczne) {
                    fail("nachodzące na siebie strefy klikalne: " + opis);
                }
            }
        }
    }
}
