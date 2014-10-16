package au.com.codeka.common.simulation;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.Colony;
import au.com.codeka.common.messages.EmpirePresence;
import au.com.codeka.common.messages.Star;

/** The {@link SimulationStatus} entry for a single empire. */
public class EmpireStatus {
    private final Star star;
    private final String empireKey;
    private EmpirePresence.Builder empirePresence;

    public List<ColonyStatus> colonies;
    public float totalGoods;
    public float totalMinerals;
    public float maxGoods;
    public float maxMinerals;
    public float deltaGoodsPerHour;
    public float deltaMineralsPerHour;
    public float totalPopulation;
    public float totalTaxPerHour;
    public DateTime goodsZeroTime;

    public EmpireStatus(Star star, String empireKey) {
        this.star = star;
        this.empireKey = empireKey;

        for (EmpirePresence ep : star.empires) {
            if (Helper.sameEmpire(ep.empire_key, empireKey)) {
                empirePresence = new EmpirePresence.Builder(ep);
                break;
            }
        }
        if (empirePresence == null) {
            totalGoods = 50.0f;
            totalMinerals = 50.0f;
            maxGoods = 50.0f;
            maxMinerals = 50.0f;
        } else {
            totalGoods = empirePresence.total_goods;
            totalMinerals = empirePresence.total_minerals;
            maxGoods = empirePresence.max_goods;
            maxMinerals = empirePresence.max_minerals;
            if (empirePresence.goods_zero_time != null) {
                goodsZeroTime = new DateTime(empirePresence.goods_zero_time * 1000);
            }
        }

        colonies = new ArrayList<ColonyStatus>();
        for (Colony colony : star.colonies) {
            if (Helper.sameEmpire(colony.empire_key, empireKey)) {
                colonies.add(new ColonyStatus(star, colony));
            }
        }
    }
}
