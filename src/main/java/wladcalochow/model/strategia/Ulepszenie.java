package wladcalochow.model.strategia;

/** Trwała inwestycja w loch. Każda ma poziomy od 0 do {@link #MAX_POZIOM}. */
public enum Ulepszenie {
    KOPALNIA("Kopalnia", "+" + Math.round(Efekty.KOPALNIA * 100) + "% wydobycia złota na poziom."),
    PULAPKI("Pułapki", "Bohaterowie zaczynają walkę ranni: -" + Math.round(Efekty.PULAPKI * 100) + "% życia na poziom."),
    ZAMEK_SKARBCA("Zamek skarbca", "-" + Math.round(Efekty.ZAMEK * 100) + "% szans kradzieży i wielkości łupu na poziom.");

    // Pięć poziomów: tanie pierwsze pomagają wcześnie się wzmocnić, drogie ostatnie to cel na nadmiar złota.
    public static final int MAX_POZIOM = 5;
    private static final int[] KOSZTY = {80, 180, 350, 600, 1000};

    /** Efekt jednego poziomu ulepszenia. */
    public static final double KOPALNIA_NA_POZIOM = Efekty.KOPALNIA;
    public static final double PULAPKI_NA_POZIOM = Efekty.PULAPKI;
    public static final double ZAMEK_NA_POZIOM = Efekty.ZAMEK;

    private static final class Efekty {
        static final double KOPALNIA = 0.20;
        static final double PULAPKI = 0.08;
        static final double ZAMEK = 0.18;
    }

    private final String nazwa;
    private final String opis;

    Ulepszenie(String nazwa, String opis) {
        this.nazwa = nazwa;
        this.opis = opis;
    }

    public String nazwa() {
        return nazwa;
    }

    public String opis() {
        return opis;
    }

    /** Bazowy koszt wejścia na dany poziom (1..MAX_POZIOM). */
    public int koszt(int poziom) {
        if (poziom < 1 || poziom > MAX_POZIOM) {
            throw new IllegalArgumentException("Nie ma poziomu " + poziom);
        }
        return KOSZTY[poziom - 1];
    }
}
