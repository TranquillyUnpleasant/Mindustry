package mindustry.ai.formations.patterns;

import arc.math.*;
import arc.math.geom.*;
import mindustry.ai.formations.*;

public class CircleFormation extends FormationPattern{
    /** Angle offset. */
    public float angleOffset = 0;

    @Override
    public Vec3 calculateSlotLocation(Vec3 outLocation, int slotNumber){
        if(slots > 8){ // Custom formation implementation
            float rows = Mathf.ceil(slots / 8f);
            float row = slotNumber % rows * .5f + 1;
            float angle = (360f * (slotNumber / rows)) / (slots / rows);
            float radius = 52.2625186f; // Same as 8 slots
            outLocation.set(Angles.trnsx(angle, radius) * row, Angles.trnsy(angle, radius) * row, angle);
        }else if(slots > 1){
            float angle = (360f * slotNumber) / slots;
            float radius = spacing / (float)Math.sin(180f / slots * Mathf.degRad);
            outLocation.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius), angle);
        }else{
            outLocation.set(0, spacing * 1.1f, 360f * slotNumber);
        }

        outLocation.z += angleOffset;

        return outLocation;
    }

}
