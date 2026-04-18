package com.nguyendevs.freesia.citizens;

import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;

@TraitName("ysm_model")
public class YsmModelTrait extends Trait {

    @Persist("model")
    private String modelId = "";

    public YsmModelTrait() {
        super("ysm_model");
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
}
