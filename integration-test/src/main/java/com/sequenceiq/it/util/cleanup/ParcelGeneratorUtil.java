package com.sequenceiq.it.util.cleanup;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.cloudera.api.swagger.model.ApiParcel;
import com.cloudera.api.swagger.model.ApiParcelState;

@Component
public class ParcelGeneratorUtil {

    /**
     * Provides a CDH <code>ApiParcel</code> instance with some predefined values with the status if <code>ACTIVATED</code>.
     *
     * @return a minimal configuration of a CDH ApiParcel instance
     */
    public ApiParcel getActivatedCDHParcel() {
        ApiParcel parcel = new ApiParcel();
        ApiParcelState state = new ApiParcelState();
        state.setCount(new BigDecimal(0));
        parcel.setProduct("CDH");
        parcel.setState(state);
        parcel.setVersion("7.1.0-1.cdh7.1.0.p0.1425634");
        parcel.setStage("ACTIVATED");
        return parcel;
    }

}
