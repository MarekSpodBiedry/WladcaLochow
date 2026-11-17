package wladcalochow.ui;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wladcalochow.model.DzienGry;
import wladcalochow.silnik.Gra;

/**
 * Zrzuty ekranu do README. Gra rysuje ekran w pamięci, a ten test zapisuje jego zawartość (znak, kolory,
 * pogrubienie każdej komórki) do plików target/podglady/*.tsv. Obrazki z kolorowymi emoji rysuje z nich
 * skrypt narzedzia/zrob-podglady.py. Uruchamia się tylko na żądanie:
 * {@code mvn test -Dtest=PodgladyEkranuTest -Dpodglady=true}, potem {@code python narzedzia/zrob-podglady.py}.
 */
class PodgladyEkranuTest {

    /** Taki sam rozmiar jak dotychczasowe zrzuty w README (1120 × 672 pikseli przy komórce 10 × 21). */
    private static final int SZER = 112;
    private static final int WYS = 32;

    static void zapiszKomorki(Ekran e, Path plik) throws Exception {
        StringBuilder s = new StringBuilder();
        s.append(e.szer).append('\t').append(e.wys).append('\n');
        for (int y = 0; y < e.wys; y++) {
            for (int x = 0; x < e.szer; x++) {
                s.append(x).append('\t').append(y).append('\t').append(e.glif(x, y)).append('\t')
                        .append(Integer.toHexString(e.kolor(x, y))).append('\t')
                        .append(Integer.toHexString(e.tlo(x, y))).append('\t')
                        .append(e.gruby(x, y) ? 1 : 0).append('\n');
            }
        }
        Files.createDirectories(plik.getParent());
        Files.writeString(plik, s.toString(), StandardCharsets.UTF_8);
    }

    /** Rozgrywa grę automatem (Enter) do poranka zadanego dnia, bez otwartego okna. */
    private static Aplikacja rozegrajDo(Path katalog, long ziarno, int dzien) {
        Aplikacja aplikacja = new Aplikacja(new KonsolaBezEkranu(SZER, WYS), katalog, ziarno, katalog.resolve("claude"));
        Gra gra = Gra.nowa(ziarno);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        aplikacja.pokazGre(gra, 1);
        aplikacja.rysuj();
        for (int krok = 0; krok < 20_000 && !gra.czyKoniecGry(); krok++) {
            if (gra.getNumerDnia() >= dzien && !aplikacja.czyDialog() && gra.getDzien().getFaza() == DzienGry.Faza.PORANEK) {
                break;
            }
            // Każdy klawisz po narysowaniu klatki: skróty i Enter zależą od tego, co jest na ekranie.
            aplikacja.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER));
            aplikacja.rysuj();
        }
        return aplikacja;
    }

    @Test
    void zapiszZrzutyDoReadme(@TempDir Path katalog) throws Exception {
        assumeTrue(Boolean.getBoolean("podglady"), "tylko na żądanie: -Dpodglady=true");
        Path cel = Path.of("target", "podglady");

        Aplikacja poranek = rozegrajDo(katalog.resolve("a"), 7, 9);
        poranek.rysuj();
        zapiszKomorki(poranek.klatka(), cel.resolve("podglad-poranek.tsv"));

        // Walka: pierwszy dzień z co najmniej trzema bohaterami, zdjęcie po kilku ruchach.
        for (long ziarno = 3; ziarno < 60; ziarno++) {
            Aplikacja walka = rozegrajDo(katalog.resolve("w" + ziarno), ziarno, 12);
            Gra gra = walka.gra();
            if (gra.czyKoniecGry() || gra.getDzien().getBohaterowie().size() < 3) {
                continue;
            }
            walka.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER));
            walka.rysuj();
            for (int i = 0; i < 7 && gra.getDzien().getWalka() != null && !gra.getDzien().getWalka().czyZakonczona(); i++) {
                walka.obsluz(new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER));
                walka.rysuj();
            }
            if (gra.getDzien().getWalka() != null && !gra.getDzien().getWalka().czyZakonczona()) {
                zapiszKomorki(walka.klatka(), cel.resolve("podglad-walka.tsv"));
                return;
            }
        }
        throw new IllegalStateException("nie znaleziono walki do zdjęcia");
    }
}
