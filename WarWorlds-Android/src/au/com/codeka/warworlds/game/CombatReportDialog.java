package au.com.codeka.warworlds.game;

import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.StyledDialog;
import au.com.codeka.warworlds.model.CombatReport;
import au.com.codeka.warworlds.model.Empire;
import au.com.codeka.warworlds.model.EmpireManager;
import au.com.codeka.warworlds.model.MyEmpire;
import au.com.codeka.warworlds.model.ShipDesign;
import au.com.codeka.warworlds.model.ShipDesignManager;
import au.com.codeka.warworlds.model.SpriteDrawable;
import au.com.codeka.warworlds.model.Star;

public class CombatReportDialog extends DialogFragment {
    private Star mStar;
    private String mCombatReportKey;
    private View mView;
    private ReportAdapter mReportAdapter;
    private TreeMap<String, Empire> mEmpires;

    public void loadCombatReport(Star star, String combatReportKey) {
        mStar = star;
        mCombatReportKey = combatReportKey;

        if (mEmpires == null) {
            mEmpires = new TreeMap<String, Empire>();;
            mEmpires.put("", EmpireManager.getInstance().getNativeEmpire());
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        mView = inflater.inflate(R.layout.combat_report_dlg, null);

        final View progressBar = mView.findViewById(R.id.progress_bar);
        final ListView reportItems = (ListView) mView.findViewById(R.id.report_items);

        progressBar.setVisibility(View.VISIBLE);
        reportItems.setVisibility(View.GONE);

        mReportAdapter = new ReportAdapter();
        reportItems.setAdapter(mReportAdapter);

        EmpireManager.getInstance().getEmpire().fetchCombatReport(
                mStar.getKey(), mCombatReportKey, 
                new MyEmpire.FetchCombatReportCompleteHandler() {
            @Override
            public void onComplete(CombatReport report) {
                progressBar.setVisibility(View.GONE);
                reportItems.setVisibility(View.VISIBLE);

                TreeSet<String> empireKeys = new TreeSet<String>();
                for (CombatReport.CombatRound round : report.getCombatRounds()) {
                    for (CombatReport.FleetSummary fleet : round.getFleets()) {
                        if (fleet.getEmpireKey() == null || fleet.getEmpireKey().length() == 0) {
                            continue;
                        }
                        if (mEmpires.containsKey(fleet.getEmpireKey())) {
                            continue;
                        }
                        if (!empireKeys.contains(fleet.getEmpireKey())) {
                            empireKeys.add(fleet.getEmpireKey());
                        }
                    }
                }
                for (String empireKey : empireKeys) {
                    EmpireManager.getInstance().fetchEmpire(empireKey,
                            new EmpireManager.EmpireFetchedHandler() {
                                @Override
                                public void onEmpireFetched(Empire empire) {
                                    mEmpires.put(empire.getKey(), empire);
                                    mReportAdapter.notifyDataSetChanged();
                                }
                            });
                }

                mReportAdapter.setReport(report);
            }
        });

        StyledDialog.Builder b = new StyledDialog.Builder(getActivity());
        b.setView(mView);

        b.setNeutralButton("Close", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        return b.create();
    }

    private class ReportAdapter extends BaseAdapter {
        private CombatReport mCombatReport;
        private ArrayList<Record> mRecords;

        private static final int ROUND_HEADER_TYPE = 0;
        private static final int ROUND_ENTRY_TYPE = 1;

        public void setReport(CombatReport combatReport) {
            mCombatReport = combatReport;
            mRecords = new ArrayList<Record>();
            for (CombatReport.CombatRound round : mCombatReport.getCombatRounds()) {
                mRecords.add(new Record(round));
                for (CombatReport.FleetJoinedRecord record : round.getFleetJoinedRecords()) {
                    mRecords.add(new Record(record));
                }
                for (CombatReport.FleetTargetRecord record : round.getFleetTargetRecords()) {
                    mRecords.add(new Record(record));
                }
                for (CombatReport.FleetAttackRecord record : round.getFleetAttackRecords()) {
                    mRecords.add(new Record(record));
                }
                for (CombatReport.FleetDamagedRecord record : round.getFleetDamagedRecords()) {
                    mRecords.add(new Record(record));
                }
            }

            notifyDataSetChanged();
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            if (mRecords == null)
                return 0;

            if (mRecords.get(position).combatRound != null) {
                return ROUND_HEADER_TYPE;
            } else {
                return ROUND_ENTRY_TYPE;
            }
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public int getCount() {
            return (mRecords != null ? mRecords.size() : 0);
        }

        @Override
        public Object getItem(int position) {
            return mRecords.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Record record = mRecords.get(position);
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = getActivity().getLayoutInflater();
                if (record.combatRound != null) {
                    view = inflater.inflate(R.layout.combat_report_header_row, null);
                } else {
                    view = inflater.inflate(R.layout.combat_report_entry_row, null);
                }
            }

            if (record.combatRound != null) {
                int roundIndex = 0;
                for (; roundIndex < mCombatReport.getCombatRounds().size(); roundIndex++) {
                    if (mCombatReport.getCombatRounds().get(roundIndex) == record.combatRound)
                        break;
                }

                TextView roundNumber = (TextView) view.findViewById(R.id.round_number);
                TextView roundTime = (TextView) view.findViewById(R.id.round_time);

                roundNumber.setText(String.format(Locale.ENGLISH, "Round #%d",
                                                  roundIndex + 1));
                roundTime.setText(String.format(Locale.ENGLISH, "%d min",
                                                  roundIndex));
            } else {
                ImageView fleetIcon = (ImageView) view.findViewById(R.id.fleet_icon);
                ImageView empireIcon = (ImageView) view.findViewById(R.id.empire_icon);
                ImageView targetFleetIcon = (ImageView) view.findViewById(R.id.target_fleet_icon);
                ImageView targetEmpireIcon = (ImageView) view.findViewById(R.id.target_empire_icon);
                TextView recordTitle = (TextView) view.findViewById(R.id.record_title);
                TextView recordDescription = (TextView) view.findViewById(R.id.record_description);

                empireIcon.setImageBitmap(null);
                targetFleetIcon.setImageDrawable(null);
                targetEmpireIcon.setImageBitmap(null);
                recordDescription.setText("");

                ShipDesignManager sdm = ShipDesignManager.getInstance();

                if (record.fleetJoinedRecord != null) {
                    CombatReport.FleetSummary fleet = record.fleetJoinedRecord.getFleet();
                    ShipDesign design = sdm.getDesign(fleet.getDesignID());
                    Empire empire = mEmpires.get(fleet.getEmpireKey());

                    recordTitle.setText("Fleet Joined Battle");
                    fleetIcon.setImageDrawable(new SpriteDrawable(design.getSprite()));
                    if (empire != null) {
                        empireIcon.setImageBitmap(empire.getShield(getActivity()));
                        recordDescription.setText(String.format(Locale.ENGLISH,
                                "%s %s joined the battle",
                                empire.getDisplayName(),
                                getFleetName(design, fleet.getNumShips())));
                    }
                } else if (record.fleetTargetRecord != null) {
                    CombatReport.FleetSummary fleet = record.fleetTargetRecord.getFleet();
                    CombatReport.FleetSummary target = record.fleetTargetRecord.getTarget();
                    ShipDesign design = sdm.getDesign(fleet.getDesignID());
                    ShipDesign targetDesign = sdm.getDesign(target.getDesignID());
                    Empire empire = mEmpires.get(fleet.getEmpireKey());
                    Empire targetEmpire = mEmpires.get(target.getEmpireKey());

                    recordTitle.setText("Fleet Targetted");
                    fleetIcon.setImageDrawable(new SpriteDrawable(design.getSprite()));
                    targetFleetIcon.setImageDrawable(new SpriteDrawable(targetDesign.getSprite()));
                    if (empire != null && targetEmpire != null) {
                        empireIcon.setImageBitmap(empire.getShield(getActivity()));
                        targetEmpireIcon.setImageBitmap(targetEmpire.getShield(getActivity()));

                        recordDescription.setText(String.format(Locale.ENGLISH,
                                "%s %s targetted %s %s",
                                empire.getDisplayName(), getFleetName(design, fleet.getNumShips()),
                                targetEmpire.getDisplayName(), getFleetName(targetDesign, target.getNumShips())));
                    }
                } else if (record.fleetAttackRecord != null) {
                    CombatReport.FleetSummary fleet = record.fleetAttackRecord.getFleet();
                    CombatReport.FleetSummary target = record.fleetAttackRecord.getTarget();
                    ShipDesign design = sdm.getDesign(fleet.getDesignID());
                    ShipDesign targetDesign = sdm.getDesign(target.getDesignID());
                    Empire empire = mEmpires.get(fleet.getEmpireKey());
                    Empire targetEmpire = mEmpires.get(target.getEmpireKey());

                    recordTitle.setText("Fleet Attacked");
                    fleetIcon.setImageDrawable(new SpriteDrawable(design.getSprite()));
                    targetFleetIcon.setImageDrawable(new SpriteDrawable(targetDesign.getSprite()));
                    if (empire != null && targetEmpire != null) {
                        empireIcon.setImageBitmap(empire.getShield(getActivity()));
                        targetEmpireIcon.setImageBitmap(targetEmpire.getShield(getActivity()));

                        recordDescription.setText(String.format(Locale.ENGLISH,
                                "%s %s attacked %s %s for %.1f points",
                                empire.getDisplayName(), getFleetName(design, fleet.getNumShips()),
                                targetEmpire.getDisplayName(), getFleetName(targetDesign, target.getNumShips()),
                                record.fleetAttackRecord.getDamage()));
                    }
                } else if (record.fleetDamagedRecord != null) {
                    CombatReport.FleetSummary fleet = record.fleetDamagedRecord.getFleet();
                    ShipDesign design = sdm.getDesign(fleet.getDesignID());
                    Empire empire = mEmpires.get(fleet.getEmpireKey());

                    recordTitle.setText("Fleet Damaged");
                    fleetIcon.setImageDrawable(new SpriteDrawable(design.getSprite()));
                    if (empire != null) {
                        empireIcon.setImageBitmap(empire.getShield(getActivity()));

                        recordDescription.setText(String.format(Locale.ENGLISH,
                                "%s %s was hit for %.1f damage",
                                empire.getDisplayName(), getFleetName(design, fleet.getNumShips()),
                                record.fleetDamagedRecord.getDamage()));
                    }
                }
            }

            return view;
        }

        private String getFleetName(ShipDesign design, float numShips) {
            int intNumShips = (int)(Math.ceil(numShips));
            if (intNumShips == 1) {
                return design.getDisplayName();
            } else {
                return String.format(Locale.ENGLISH, "%s (× %d)",
                        design.getDisplayName(), intNumShips);
            }
        }

        private class Record {
            // only one of these will be non-null and it represents the record
            // we're displaying in this particular row

            // if non-null, this is the "header" row for that round.
            public CombatReport.CombatRound combatRound;

            public CombatReport.FleetJoinedRecord fleetJoinedRecord;
            public CombatReport.FleetTargetRecord fleetTargetRecord;
            public CombatReport.FleetAttackRecord fleetAttackRecord;
            public CombatReport.FleetDamagedRecord fleetDamagedRecord;

            public Record(CombatReport.CombatRound combatRound) {
                this.combatRound = combatRound;
            }
            public Record(CombatReport.FleetJoinedRecord record) {
                this.fleetJoinedRecord = record;
            }
            public Record(CombatReport.FleetTargetRecord record) {
                this.fleetTargetRecord = record;
            }
            public Record(CombatReport.FleetAttackRecord record) {
                this.fleetAttackRecord = record;
            }
            public Record(CombatReport.FleetDamagedRecord record) {
                this.fleetDamagedRecord = record;
            }
        }
    }
}
