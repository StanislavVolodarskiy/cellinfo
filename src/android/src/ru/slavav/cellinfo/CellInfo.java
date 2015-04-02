
package ru.slavav.cellinfo;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Build;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;


public class CellInfo extends CordovaPlugin {
    private static final String TAG = "CellInfo";

    private static class GsmCell {
        public final Integer id;
        public final Integer psc;
        public final Integer lac;

        public GsmCell(GsmCellLocation location) {
            id  = cast(location.getCid(), -1);
            psc = cast(location.getPsc(), -1);
            lac = cast(location.getLac(), -1);
        }

        public GsmCell(NeighboringCellInfo cellInfo) {
            id  = cast(cellInfo.getCid(), NeighboringCellInfo.UNKNOWN_CID);
            psc = cast(cellInfo.getPsc(), NeighboringCellInfo.UNKNOWN_CID);
            lac = cast(cellInfo.getLac(), NeighboringCellInfo.UNKNOWN_CID);
        }

        @Override
        public final boolean equals(Object object) {
            if (!(object instanceof GsmCell)) {
                return false;
            }
            GsmCell other = (GsmCell) object;
            return eq(id, other.id) && eq(psc, other.psc) && eq(lac, other.lac);
        }

        @Override
        public final int hashCode() {
            return hash(hash(hash(1, id), psc), lac);
        }

        private static Integer cast(int value, int invalidValue) {
            return (value == invalidValue) ? null : value;
        }

        private static boolean eq(Object object1, Object object2) {
            return (object1 == null) ? object2 == null : object1.equals(object2);
        }

        private static int hash(int hash, Object object) {
            hash = hash * 37 + Boolean.valueOf(object != null).hashCode();
            hash = hash * 37 + ((object == null) ? 0 : object.hashCode());
            return hash;
        }
    }

    PhoneStateListener listener = new PhoneStateListener() {
        @Override
        public void onCellLocationChanged(CellLocation location) {
            Log.d(TAG, "onCellLocationChanged");
            if (location instanceof GsmCellLocation) {
                gsmCell = new GsmCell((GsmCellLocation) location);
            }
        }
    };

    private TelephonyManager tm;
    private Integer          mcc;
    private Integer          mnc;
    private GsmCell          gsmCell;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        tm = getTelephonyManager();
        if (tm == null) {
            Log.d(TAG, "Can not create TelephonyManager.");
        }

        mcc = null;
        mnc = null;
        if (tm != null) {
            String mccAndMnc = tm.getNetworkOperator();
            if (mccAndMnc != null && mccAndMnc.length() > 3) {
                try {
                    mcc = Integer.parseInt(mccAndMnc.substring(0, 3));
                } catch (NumberFormatException e) {
                }
                try {
                    mnc = Integer.parseInt(mccAndMnc.substring(3));
                } catch (NumberFormatException e) {
                }
            }
        }

        startListening();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("gsmCells")) {
            return gsmCells(callbackContext);
        }
        return false;
    }

    @Override
    public void onPause(boolean multitasking) {
        stopListening();
    }

    @Override
    public void onResume(boolean multitasking) {
        startListening();
    }

    private boolean gsmCells(CallbackContext callbackContext) throws JSONException {
        if (tm == null) {
            callbackContext.error("Can not create TelephonyManager.");
            return false;
        }

        Set<GsmCell> cells = new HashSet<GsmCell>();
        if (gsmCell != null) {
            cells.add(gsmCell);
        }

        if (Build.VERSION.SDK_INT >= 17) {
            List<android.telephony.CellInfo> cellList = tm.getAllCellInfo();
            if (cellList == null) {
                Log.d(TAG, "TelephonyManager.getAllCellInfo returns null.");
            } else {
                Log.d(TAG, "TelephonyManager.getAllCellInfo has not been supported yet.");
                // TODO: process cellList
            }
        }

        List<NeighboringCellInfo> cellList = tm.getNeighboringCellInfo();
        if (cellList == null) {
            Log.d(TAG, "TelephonyManager.getNeighboringCellInfo returns null.");
        } else {
            for (NeighboringCellInfo cell: cellList) {
                cells.add(new GsmCell(cell));
            }
        }

        JSONArray answer = new JSONArray();
        for (GsmCell cell: cells) {
            JSONObject item = new JSONObject();
            item.put("countrycode", mcc);
            item.put("operatorid", mnc);
            item.put("id", cell.id);
            item.put("psc", cell.psc);
            item.put("lac", cell.lac);
            answer.put(item);
        }
        callbackContext.success(answer);
        return true;
    }

    private TelephonyManager getTelephonyManager() {
        Context context = cordova.getActivity().getApplicationContext();
        if (context == null) {
            return null;
        }
        return (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    private void startListening() {
        if (tm != null) {
            Log.d(TAG, "Start listening.");
            tm.listen(listener, PhoneStateListener.LISTEN_CELL_LOCATION);
        }
    }

    private void stopListening() {
        if (tm != null) {
            Log.d(TAG, "Stop listening.");
            tm.listen(listener, PhoneStateListener.LISTEN_NONE);
        }
    }
}

