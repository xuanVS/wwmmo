package au.com.codeka.common.model;

import au.com.codeka.common.messages.BuildRequest;

public enum DesignKind {
    BUILDING(1),
    SHIP(2);

    private int mValue;

    DesignKind(int value) {
        mValue = value;
    }

    public int getValue() {
        return mValue;
    }

    public static DesignKind fromNumber(int value) {
        for(DesignKind dk : DesignKind.values()) {
            if (dk.getValue() == value) {
                return dk;
            }
        }

        return DesignKind.BUILDING;
    }

    public static DesignKind fromBuildKind(BuildRequest.BUILD_KIND buildKind) {
        switch (buildKind) {
        case BUILDING:
            return DesignKind.BUILDING;
        case SHIP:
            return DesignKind.SHIP;
        default:
            throw new IllegalArgumentException("Unknown BuildKind: " + buildKind);
        }
    }
}
