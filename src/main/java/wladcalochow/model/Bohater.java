package wladcalochow.model;

/** Bohater atakujący loch. Przychodzi po złoto Władcy. */
public abstract class Bohater extends Istota {

    private static final long serialVersionUID = 1L;

    protected Bohater(String imie, int silaAtaku, int maxPunktyZycia, int maxMorale, int zloto) {
        super(imie, silaAtaku, maxPunktyZycia, maxMorale, zloto);
    }

    /** Skalowanie trudności: bohaterowie w późniejszych dniach są silniejsi. */
    public void zahartuj(double procent) {
        if (procent > 0) {
            wzmocnij(procent);
        }
    }
}
