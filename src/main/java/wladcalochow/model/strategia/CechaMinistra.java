package wladcalochow.model.strategia;

/**
 * Mocna strona ministra. Każdy minister ma jedną cechę i jedną wadę, więc nie ma jednego najlepszego
 * ministra na całą grę: wybiera się takiego, którego cecha pasuje do obecnego kłopotu lochu.
 */
public enum CechaMinistra {
    NEGOCJATOR("Negocjator", "Łapówki dla bohaterów są o 25% tańsze.", true),
    LICHWIARZ("Lichwiarz", "Co rano złoto w skarbcu przynosi 2% odsetek (najwyżej 150 dziennie).", true),
    MEDYK("Medyk", "Ranni wracają do sił dwa razy szybciej.", false),
    TRENER("Trener", "Wojownicy zdobywają w walce dwa razy więcej doświadczenia.", false),
    WERBOWNIK("Werbownik", "W kolejce do pracy dwa razy częściej stają ogry.", false),
    NADZORCA("Nadzorca", "Kopalnia wydobywa o 25% więcej złota.", false);

    private final String nazwa;
    private final String opis;
    private final boolean finansowa;

    CechaMinistra(String nazwa, String opis, boolean finansowa) {
        this.nazwa = nazwa;
        this.opis = opis;
        this.finansowa = finansowa;
    }

    public String nazwa() {
        return nazwa;
    }

    public String opis() {
        return opis;
    }

    /** Czy cecha przysługuje Ministrowi Finansów (w przeciwnym razie Ministrowi Pracy). */
    public boolean finansowa() {
        return finansowa;
    }
}
