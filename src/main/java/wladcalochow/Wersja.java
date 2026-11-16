package wladcalochow;

/** Numer wersji gry, wpisywany do pliku .jar podczas budowania (z pom.xml). */
public final class Wersja {

    /** Wersja gry uruchomionej prosto ze źródeł, bez zbudowanego pliku .jar. */
    public static final String ROBOCZA = "robocza";

    private Wersja() {
    }

    public static String numer() {
        String numer = Wersja.class.getPackage().getImplementationVersion();
        return numer == null || numer.isBlank() ? ROBOCZA : numer;
    }

    public static boolean czyWydanie() {
        return !ROBOCZA.equals(numer());
    }
}
