package wladcalochow.model.strategia;

/** Trwała inwestycja w loch. Każda ma poziomy od 0 do {@link #MAX_POZIOM}. */
public enum Ulepszenie {
    KOPALNIA("Kopalnia", "+25% wydobycia złota na poziom."),
    PULAPKI("Pułapki", "Bohaterowie zaczynają walkę ranni: -10% życia na poziom."),
    ZAMEK_SKARBCA("Zamek skarbca", "-25% szans kradzieży i wielkości łupu na poziom.");

    public static final int MAX_POZIOM = 3;
    private static final int[] KOSZTY = {150, 350, 700};

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
