package wladcalochow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprawdza w tle, czy na GitHubie jest nowsze wydanie gry.
 * Brak sieci, limit zapytań albo dowolny błąd oznaczają po prostu "nic nie wiadomo": gra działa dalej.
 */
public final class Aktualizacje {

    public static final String REPOZYTORIUM = "MarekSpodBiedry/WladcaLochow";
    public static final String STRONA_WYDAN = "https://github.com/" + REPOZYTORIUM + "/releases/latest";
    public static final String ADRES_INSTALATORA =
            "https://raw.githubusercontent.com/" + REPOZYTORIUM + "/main/install.ps1";
    /** Polecenie PowerShella, które instaluje albo aktualizuje grę na Windows. */
    public static final String POLECENIE_INSTALATORA = "irm " + ADRES_INSTALATORA + " | iex";

    private static final String ADRES_API = "https://api.github.com/repos/" + REPOZYTORIUM + "/releases/latest";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?(\\d+(?:\\.\\d+)*)\"");
    private static final Duration LIMIT = Duration.ofSeconds(4);

    private Aktualizacje() {
    }

    /** Wyciąga numer wersji z odpowiedzi GitHuba o najnowszym wydaniu. */
    static Optional<String> wersjaZOdpowiedzi(String json) {
        Matcher dopasowanie = TAG.matcher(json == null ? "" : json);
        return dopasowanie.find() ? Optional.of(dopasowanie.group(1)) : Optional.empty();
    }

    /** Porównuje numery wersji część po części: 1.10.0 jest nowsze niż 1.9.3. */
    public static boolean czyNowsza(String kandydat, String obecna) {
        try {
            String[] a = kandydat.split("\\.");
            String[] b = obecna.split("\\.");
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int x = i < a.length ? Integer.parseInt(a[i]) : 0;
                int y = i < b.length ? Integer.parseInt(b[i]) : 0;
                if (x != y) {
                    return x > y;
                }
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Numer najnowszego wydania albo pusty wynik, gdy nie udało się go ustalić. */
    public static Optional<String> najnowszeWydanie() {
        try {
            HttpClient klient = HttpClient.newBuilder().connectTimeout(LIMIT).build();
            // WLADCA_API pozwala w testach podstawić własny adres (tak samo jak w instalatorze).
            String nadpisany = System.getenv("WLADCA_API");
            String adres = nadpisany == null || nadpisany.isBlank() ? ADRES_API : nadpisany;
            HttpRequest zapytanie = HttpRequest.newBuilder(URI.create(adres))
                    .timeout(LIMIT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "WladcaLochow")
                    .GET()
                    .build();
            HttpResponse<String> odpowiedz = klient.send(zapytanie, HttpResponse.BodyHandlers.ofString());
            return odpowiedz.statusCode() == 200 ? wersjaZOdpowiedzi(odpowiedz.body()) : Optional.empty();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /** Sprawdza w tle; wynik zawiera numer wersji tylko wtedy, gdy jest nowsza od obecnej. */
    public static CompletableFuture<Optional<String>> sprawdzWTle(String obecna) {
        CompletableFuture<Optional<String>> wynik = new CompletableFuture<>();
        Thread watek = new Thread(() -> wynik.complete(
                najnowszeWydanie().filter(najnowsza -> czyNowsza(najnowsza, obecna))), "aktualizacje");
        watek.setDaemon(true);
        watek.start();
        return wynik;
    }
}
