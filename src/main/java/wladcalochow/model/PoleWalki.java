package wladcalochow.model;

import java.util.List;

/** To, co istota widzi podczas walki, gdy podejmuje decyzję. */
public interface PoleWalki {

    /** Przeciwnicy danej istoty, którzy nadal biorą udział w walce. */
    List<Istota> przeciwnicy(Istota kto);

    /** Sojusznicy danej istoty (razem z nią), którzy nadal biorą udział w walce. */
    List<Istota> sojusznicy(Istota kto);
}
