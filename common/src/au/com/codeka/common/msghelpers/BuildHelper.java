package au.com.codeka.common.msghelpers;

import au.com.codeka.common.messages.BuildRequest;
import au.com.codeka.common.model.BaseDesignManager;
import au.com.codeka.common.model.Design;
import au.com.codeka.common.model.DesignKind;

public class BuildHelper {
    public static Design getDesign(BuildRequest buildRequest) {
        return BaseDesignManager.i.getDesign(DesignKind.fromBuildKind(buildRequest.build_kind),
                buildRequest.design_id);
    }

    public static Design getDesign(BuildRequest.Builder buildRequest) {
        return BaseDesignManager.i.getDesign(DesignKind.fromBuildKind(buildRequest.build_kind),
                buildRequest.design_id);
    }

}
