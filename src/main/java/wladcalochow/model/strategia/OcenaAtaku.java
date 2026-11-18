package wladcalochow.model.strategia;

/**
 * Ocena zapowiadanego ataku słowami, żeby gracz nie musiał liczyć wartości lochu i porównywać jej z łapówką.
 * Ocena opiera się na raporcie zwiadu, więc jest tak samo niepewna jak on (±25% losowości ataku,
 * a przy Niesłownym ministrze także zaszumiony raport).
 */
public enum OcenaAtaku {
    SPOKOJ("spokój", "nikt się nie zbliża"),
    SLABSZY("słabszy od lochu", "potwory powinny sobie poradzić"),
    WYROWNANY("wyrównany", "może pójść w obie strony"),
    GROZNY("groźny", "atak silniejszy od obrony lochu");

    /** Atak słabszy niż ten ułamek obrony: „słabszy od lochu”. */
    public static final double PROG_SLABSZY = 0.6;
    /** Atak co najmniej tak silny jak 80% obrony: „groźny” (ten sam próg, przy którym ekspert przekupuje). */
    public static final double PROG_GROZNY = 0.8;

    private final String nazwa;
    private final String opis;

    OcenaAtaku(String nazwa, String opis) {
        this.nazwa = nazwa;
        this.opis = opis;
    }

    public String nazwa() {
        return nazwa;
    }

    public String opis() {
        return opis;
    }

    /** Ocena na podstawie wartości zapowiadanego ataku i obrony lochu (ta sama skala). */
    public static OcenaAtaku ocen(int wartoscAtaku, int obrona, boolean spokoj) {
        if (spokoj) {
            return SPOKOJ;
        }
        double stosunek = wartoscAtaku / (double) Math.max(1, obrona);
        return stosunek < PROG_SLABSZY ? SLABSZY : stosunek <= PROG_GROZNY ? WYROWNANY : GROZNY;
    }
}
