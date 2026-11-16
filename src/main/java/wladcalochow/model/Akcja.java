package wladcalochow.model;

/** Działanie, które istota wybiera w swojej kolejce. Wykonuje je {@link Walka}. */
public sealed interface Akcja {

    record Atak(Istota cel) implements Akcja {
    }

    /** Próba kradzieży ze skarbca; opcjonalnie istota ucieka zaraz po niej. */
    record ProbaKradziezy(double szansa, boolean potemUcieczka) implements Akcja {
    }

    record Czekanie() implements Akcja {
    }
}
